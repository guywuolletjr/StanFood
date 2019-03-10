package stanford.cs194.stanfood.adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import stanford.cs194.stanfood.R;
import stanford.cs194.stanfood.fragments.BottomSheetListView;
import stanford.cs194.stanfood.fragments.EventInfoDisplay;
import stanford.cs194.stanfood.models.Event;

public class EventAdapter extends ArrayAdapter {

    private ArrayList<Event> events;
    private Context context;

    private FragmentManager supportFragment;
    private BottomSheetListView eventListView;
    private ViewGroup bottomSheetContentsView;

    public EventAdapter(
            Context context,
            ArrayList<Event> events,
            BottomSheetListView eventListView,
            ViewGroup bottomSheetContentsView,
            FragmentManager supportFragment
    ) {
        super(context, R.layout.list_view, events);
        this.context = context;
        this.events = events;
        this.eventListView = eventListView;
        this.bottomSheetContentsView = bottomSheetContentsView;
        this.supportFragment = supportFragment;
    }

    @NonNull
    public View getView(int position, final View view, @NonNull final ViewGroup parent) {
        final LayoutInflater inflater = LayoutInflater.from(context);
        final View rowView = inflater.inflate(R.layout.list_view, null, true);

        // gets references to objects in the list_view.xml file
        TextView eventLocation = bottomSheetContentsView.findViewById(R.id.bottom_sheet_header);
        TextView eventName = rowView.findViewById(R.id.eventName);
        TextView eventTimeStart = rowView.findViewById(R.id.eventTimeStart);
        TextView eventDescription = rowView.findViewById(R.id.eventDescription);

        Event event = events.get(position);
        String name = event.getName();
        final String locationName = event.getLocationName();
        long time = event.getTimeStart();
        long duration = event.getDuration();
        String description = event.getDescription();

        // sets the values of the objects to the value from the current event
        // TODO: Temporary null check until we clear out data since some events don't have explicit name fields
        if(name != null && !name.equals("")) eventName.setText(name);
        else  eventName.setText("N/A");

        if(locationName != null) eventLocation.setText(locationName);
        else eventLocation.setText("N/A");

        if(time != 0) eventTimeStart.setText(getEventTimeRange(time, duration));
        else eventTimeStart.setText("N/A");

        if(!description.equals("")) eventDescription.setText(description);
        else eventDescription.setText("N/A");

        rowView.setOnClickListener(new View.OnClickListener(){
            /**
             * When list item is clicked on, display the event information.
             * @param listItemView
             */
            @Override
            public void onClick(View listItemView) {
                String clickedEventName = ((TextView)listItemView.findViewById(R.id.eventName)).getText().toString();
                String clickedTimeRange = ((TextView)listItemView.findViewById(R.id.eventTimeStart)).getText().toString();
                String clickedEventDescription = ((TextView)listItemView.findViewById(R.id.eventDescription)).getText().toString();

                TextView infoHeader = bottomSheetContentsView.findViewById(R.id.bottom_sheet_header);
                String clickedLocationName = infoHeader.getText().toString();

                EventInfoDisplay infoDisplay = new EventInfoDisplay(context, clickedEventName, clickedLocationName, clickedTimeRange, clickedEventDescription, supportFragment);
                infoDisplay.displayInfo(bottomSheetContentsView);

            }
        });

        return rowView;
    }

    /**
     * Given the start time and duration, returns the time range.
     *
     * E.g. given [Mon Jan 15, 4:30PM] and duration [90 min] in milliseconds,
     * return the string "Mon Jan 15, 4:30PM - 6:00PM"
     */
    private String getEventTimeRange(long startTimeInMillis, long durationInMillis) {
        // set start time
        Calendar startTime = Calendar.getInstance();
        startTime.setTimeInMillis(startTimeInMillis);
        // set end time
        Calendar endTime = Calendar.getInstance();
        endTime.setTimeInMillis(startTimeInMillis + durationInMillis);

        SimpleDateFormat startFormat = new SimpleDateFormat(/*"E MMM dd,*/"hh:mm", Locale.US);
        SimpleDateFormat endFormat = new SimpleDateFormat("hh:mma", Locale.US);
        String startTimeStr = startFormat.format(startTime.getTime());
        String endTimeStr = endFormat.format(endTime.getTime());
        return startTimeStr + " - " + endTimeStr;
    }

}
