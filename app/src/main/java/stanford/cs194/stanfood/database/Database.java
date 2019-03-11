package stanford.cs194.stanfood.database;

import android.location.Address;
import android.location.Geocoder;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import stanford.cs194.stanfood.App;
import stanford.cs194.stanfood.helpers.LatLngWrapper;
import stanford.cs194.stanfood.models.Event;
import stanford.cs194.stanfood.models.Food;
import stanford.cs194.stanfood.models.Pin;
import stanford.cs194.stanfood.models.Setting;
import stanford.cs194.stanfood.models.User;

public class Database {
    final private String dbPath = "https://stanfood-e7255.firebaseio.com/";
    final private String stanfordLocSuffix = " Stanford, CA 94305";
    private FirebaseDatabase database;
    public DatabaseReference dbRef;

    public Database(){
        database = FirebaseDatabase.getInstance(dbPath);
        dbRef = database.getReference();
    }

    // createEntry: creates an entry in Firebase table of Object obj. Returns object unique table key
    public String createEntry(String table, Object obj){
        DatabaseReference pushedTableRef = dbRef.child(table).push();
        pushedTableRef.setValue(obj);
        return pushedTableRef.getKey();
    }

    // createUser: creates a new user in the users table
    // Note: createUser signature differs from other create functions in that it takes in an
    // existing uid we already generated from Firebase authentication
    public String createUser(String uid, String email, String name){
        User user = new User(email, name);
        dbRef.child("users").child(uid).setValue(user);
        return uid;
    }

    // Updates the instance id of the specified user
    public void updateUserInstanceId(String userId, String instanceId) {
        dbRef.child("users").child(userId).child("instanceId").setValue(instanceId);
    }

    /**
     * Creates a new user setting in the settings table for the given user.
     */
    public void createSetting(String uid, boolean receivePush, int timeWindowStart, int timeWindowEnd) {
        Setting setting = new Setting(receivePush, timeWindowStart, timeWindowEnd);
        dbRef.child("settings").child(uid).setValue(setting);
    }

    /**
     * Creates a new default user setting in the settings table for the given user.
     * Default settings are:
     * receivePush = true
     * timeWindowStart = 0 (hour)
     * timeWindowEnd = 24 (hour)
     */
    public void createDefaultSetting(String uid) {
        createSetting(uid, true, 0, 24);
    }

    // createPin: creates a new pin in the pins table
    public String createPin(LatLng loc, String locationName){
        LatLngWrapper locW = new LatLngWrapper(loc.latitude, loc.longitude);
        return createEntry("pins", new Pin(locW, locationName));
    }

    // createEvent: creates a new event in the events table
    // first searches to see if there is a pin at the associated location
    // if not, one is created. pinId is then retrieved, allowing the
    // event to be created
    public void createEvent(final String name, final String description, final String locationName,
                            final long timeStart, final long duration, final String foodDescription,
                            final String userId, final String imagePath){
        final LatLng loc = getLocationFromName(locationName);
        // TODO: Insert check for null location in case the corresponding location name doesn't exist
        dbRef.child("pins").addListenerForSingleValueEvent(
            new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    String pinId = null;
                    for(DataSnapshot ds : dataSnapshot.getChildren()){
                        if(ds.hasChildren()) {
                            Pin curPin = ds.getValue(Pin.class);
                            LatLng coordinate = curPin.getLocationCoordinate();
                            if (loc.equals(coordinate)) {
                                pinId = ds.getKey();
                                ds.getRef().child("numEvents").setValue(curPin.getNumEvents()+1);
                                break;
                            }
                        }
                    }
                    if(pinId == null) {
                        pinId = createPin(loc, locationName);
                    }
                    String eventId = createEntry("events", new Event(pinId, name, description,
                            locationName, timeStart, duration, userId));
                    createFood(eventId, foodDescription, imagePath);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d("ERROR", databaseError.toString());
                }
            }
        );
    }

    // createFood: creates a new food item in the food table
    public void createFood(String eventId, String description, String imagePath){
        createEntry("food", new Food(eventId, description, imagePath));
    }

    /**
     * getLocationFromName: uses Geocoder package to return coordinates from a locationName
     * Currently only handles conversions of Stanford locations and building names
     * @return null if the location name does not correspond to a recognizable Stanford location.
     * Returns a LatLng object corresponding to the location if one exists.
     */
    private LatLng getLocationFromName(String locationName){
        Geocoder geo = new Geocoder(App.getContext(), Locale.US);
        String fullAddress = locationName + stanfordLocSuffix;
        LatLng loc = null;
        try {
            List<Address> addresses = geo.getFromLocationName(fullAddress, 1);
            if(addresses != null && !addresses.isEmpty()){
                Address address = addresses.get(0);
                loc = new LatLng(address.getLatitude(), address.getLongitude());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return loc;
    }
}
