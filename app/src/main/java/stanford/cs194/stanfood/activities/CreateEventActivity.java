package stanford.cs194.stanfood.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import stanford.cs194.stanfood.R;
import stanford.cs194.stanfood.database.Database;

public class CreateEventActivity extends AppCompatActivity {
    public static final long HOURS_TO_MS = 3600000;
    public static final long MINUTES_TO_MS = 60000;

    private Database db;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);
        db = new Database();
        prefs = getSharedPreferences("loginData", MODE_PRIVATE);
    }

    /**
     * Extracts event name from the layout.
     * Note: Event name required
     * @return "" (empty string) if no event name given.
     */
    private String getEventName() {
        EditText eventName = findViewById(R.id.eventName);
        String eventNameStr = eventName.getText().toString().trim();

        // Error checking for required field: Event Name
        if (eventNameStr.equals("")) {
            eventName.setError("Event name required.");
        }
        return eventNameStr;
    }

    /**
     * Extracts event description from the layout.
     * Note: not required for event creation
     * @return Event description
     */
    private String getEventDescription() {
        EditText eventDescription = findViewById(R.id.eventDescription);
        return eventDescription.getText().toString().trim();
    }

    /**
     * Extracts food description from the layout
     * Note: Food description required.
     * @return "" (empty string) if no food description given.
     */
    private String getFood() {
        EditText foodDescription = findViewById(R.id.foodDescription);
        String foodDescriptionStr = foodDescription.getText().toString().trim();

        // Error checking for required field: Food Description
        if (foodDescriptionStr.equals("")) {
            foodDescription.setError("Food description required.");
        }
        return foodDescriptionStr;
    }

    /**
     * Extracts location name from the layout
     * Note: Location name required.
     * @return "" (empty string) if no location name given.
     */
    private String getLocationName() {
        EditText eventLocation = findViewById(R.id.eventLocation);
        String eventLocationStr = eventLocation.getText().toString().trim();

        // Error checking for required field: Event Location
        if (eventLocationStr.equals("")) {
            eventLocation.setError("Event Location required.");
        }
        return eventLocationStr;
    }

    /**
     * Extracts event starting date and time from the layout in the form of milliseconds
     * Note: Event Date and Start Time both required.
     * @return 0 milliseconds if event date or start time not chosen
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private long getStartTimeMS() {
        TextView startDate = findViewById(R.id.startDate);
        TextView startTime = findViewById(R.id.startTime);
        String dateStr = startDate.getText().toString().trim();
        String timeStr = startTime.getText().toString().trim();

        // Error checking for required fields: Start time, Start date
        boolean missingStartTime = false;
        if (dateStr.equals("")) {
            startDate.requestFocus();
            startDate.setError("Event Date required.");
            missingStartTime = true;
        } else {
            startDate.setError(null);
        }
        if (timeStr.equals("")) {
            startTime.requestFocus();
            startTime.setError("Start Time required.");
            missingStartTime = true;
        } else {
            startTime.setError(null);
        }
        if (missingStartTime) {
            return 0;
        }

        // Convert inputted date and time to milliseconds
        String[] dates = dateStr.split("-");
        String[] times = timeStr.split(":");
        LocalDateTime ldt;

        // Fills LocalDateTime with inputted date and time if present, else current time
        if (dates.length == 3 && times.length == 2) {
            int year = Integer.parseInt(dates[0]);
            int month = Integer.parseInt(dates[1]);
            int day = Integer.parseInt(dates[2]);
            int hour = Integer.parseInt(times[0]);
            int minute = Integer.parseInt(times[1]);
            ldt = LocalDateTime.of(year, month, day, hour, minute);
        } else {
            ldt = LocalDateTime.now();
        }
        ZoneId timeZone = TimeZone.getDefault().toZoneId();
        ZonedDateTime zdt = ldt.atZone(timeZone);
        return zdt.toInstant().toEpochMilli();
    }

    /**
     * Extracts event duration in milliseconds from the layout.
     * Note: Some non-zero duration required, but hours and minutes don't both have to be filled.
     * @return 0 if invalid or empty duration.
     */
    private long getDurationMS(){
        EditText hoursDuration = findViewById(R.id.hours);
        EditText minutesDuration = findViewById(R.id.minutes);
        String hoursStr = hoursDuration.getText().toString().trim();
        String minutesStr = minutesDuration.getText().toString().trim();

        long hoursLong = 0;
        long minutesLong = 0;
        if (!hoursStr.equals("")) {
            hoursLong = Long.parseLong(hoursStr);
        }
        if (!minutesStr.equals("")) {
            minutesLong = Long.parseLong(minutesStr);
        }
        // Converts given hours and minutes to milliseconds
        long durationMS = hoursLong * HOURS_TO_MS + minutesLong * MINUTES_TO_MS;

        // Error checking for required field: Duration
        if (durationMS == 0) {
            hoursDuration.setError("Non-zero duration needed.");
            minutesDuration.setError("Non-zero duration needed.");
        } else {
            hoursDuration.setError(null);
            minutesDuration.setError(null);
        }
        return durationMS;
    }

    /**
     * Creates event according to filled out information
     * Contains event name, food description, location name, event description,
     * start date and time, and duration.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void createEvent(View view) {
        String eventName = getEventName();
        String eventDescription = getEventDescription();
        String foodDescription = getFood();
        String locationName = getLocationName();
        long startTimeMS = getStartTimeMS();
        long durationMS = getDurationMS();

        // Get User ID to link to event
        String userId = prefs.getString("userId", "");

        // If any fields empty/invalid, return without attempting database event creation
        if (eventName.equals("") || foodDescription.equals("") || locationName.equals("")
            || startTimeMS == 0 || durationMS == 0 || userId.equals("")) {
            return;
        }

        db.createEvent(eventName, eventDescription, locationName, startTimeMS, durationMS, foodDescription, userId);

        Context context = getApplicationContext();
        String text = "Event creation successful!";
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_SHORT);
        final int BOTTOM_SHEET_PEEK_HEIGHT = (int)context.getResources().getDimension(R.dimen.bottom_sheet_peek_height);
        toast.setGravity(Gravity.BOTTOM, 0, BOTTOM_SHEET_PEEK_HEIGHT);
        toast.show();
        finish();
    }

    /*
     * Displays a Date Picker dialog to get the user to choose a start date.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void getDate(View v) {
        final TextView dateText = findViewById(R.id.startDate);

        // Gets current date
        final ZonedDateTime zdt = ZonedDateTime.now();
        int year = zdt.getYear();
        int month = zdt.getMonthValue() - 1;
        int day = zdt.getDayOfMonth();

        // Show DatePicker dialog
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
            new DatePickerDialog.OnDateSetListener() {
                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                    LocalDate localDate = LocalDate.of(year, monthOfYear + 1, dayOfMonth);
                    dateText.setText(localDate.toString());
                }
            }, year, month, day);
        datePickerDialog.show();
    }

    /*
     * Displays a Time Picker dialog to get the user to choose a start time.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void getTime(View v) {
        final TextView timeText = findViewById(R.id.startTime);

        // Gets current time
        final ZonedDateTime zdt = ZonedDateTime.now();
        int hour = zdt.getHour();
        int minute = zdt.getMinute();

        // Show TimePicker dialog
        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
            new TimePickerDialog.OnTimeSetListener() {
                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                    LocalTime localTime = LocalTime.of(hourOfDay, minute);
                    timeText.setText(localTime.toString());
                }
            }, hour, minute, false);
        timePickerDialog.show();
    }
}