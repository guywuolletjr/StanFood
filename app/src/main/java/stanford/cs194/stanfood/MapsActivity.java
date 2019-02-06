package stanford.cs194.stanfood;

import android.content.Intent;
import android.support.design.widget.BottomSheetBehavior;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.Arrays;
import java.util.List;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, OnMarkerClickListener, GoogleMap.OnMapClickListener, GoogleMap.OnCameraMoveStartedListener {

    private GoogleMap mMap;
    private View bottomSheet;
    private BottomSheetBehavior<View> mBottomSheetBehavior;
    private DrawerLayout mDrawerLayout;

    private FusedLocationProviderClient mFusedLocationClient;
    private float distanceRange = 10000;
    private Database db;
    private Authentication auth = new Authentication();

    private List<AuthUI.IdpConfig> providers = Arrays.asList(
            new AuthUI.IdpConfig.EmailBuilder().build(),
            new AuthUI.IdpConfig.GoogleBuilder().build());
    private static final int RC_SIGN_IN = 123; // Arbitrary request code value


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Get the bottom sheet and hide it initially
        bottomSheet = findViewById(R.id.bottom_sheet);
        mBottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        mBottomSheetBehavior.setHideable(true);
        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        db = new Database();

        // Get the transparent toolbar to insert the navigation menu icon
        mDrawerLayout = findViewById(R.id.drawer_layout);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionbar = getSupportActionBar();
        actionbar.setDisplayHomeAsUpEnabled(true);
        actionbar.setHomeAsUpIndicator(R.drawable.ic_menu);
        // Set up listeners for the navigation menu
        addMenuIconListener();
        addNavigationListener();

        if (auth.getCurrentUser() != null) {
            // TODO hide log in option in menu
            Log.d("Authentication","Logged in as: " + auth.getCurrentUser().getDisplayName());
        } else {
            // TODO hide log out option in menu
            Log.d("Authentication","Not logged in");
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        //adds location marker
        enableMyLocation();

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mFusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            LatLng current = new LatLng(location.getLatitude(),location.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(current,16));
                            populatePins(location);
                        }
                    }
                });

        mMap.setOnMarkerClickListener(this);
        mMap.setOnMapClickListener(this);
        mMap.setOnCameraMoveStartedListener(this);
    }

    /**
     * Expand bottom info window when a pin is clicked.
     *
     * @param marker - the pin that is clicked
     * @return - true to indicate the action was successful
     */
    @Override
    public boolean onMarkerClick(Marker marker) {
        LatLng location = marker.getPosition();
        //String pinId = markers.get(marker.getId());
        // TODO: get text description or list of events to display
        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        mBottomSheetBehavior.setHideable(false);

        // center the marker in the map area above the bottom sheet
        mMap.setPadding(0, 0, 0, 1000);
        mMap.animateCamera(CameraUpdateFactory.newLatLng(location),500,null);
        return true;
    }

    /**
     * Listen for when map is clicked and hide bottom sheet if it is expanded.
     * @param latLng - location of click
     */
    @Override
    public void onMapClick(LatLng latLng) {
        if (mBottomSheetBehavior.getState() != BottomSheetBehavior.STATE_COLLAPSED) {
            mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    /**
     * Listen for when camera starts moving and collapse bottom sheet.
     * @param i - reason the camera motion started
     */
    @Override
    public void onCameraMoveStarted(int i) {
        if (mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            // don't want to collapse bottom sheet when a marker is
            // clicked and the app moves the camera automatically
            if (i != REASON_DEVELOPER_ANIMATION) {
                mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        // Check if location permissions are granted and if so enable the
        // location data layer.
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0
                        && grantResults[0]
                        == PackageManager.PERMISSION_GRANTED) {
                    enableMyLocation();
                    break;
                }
        }
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        } else {
            ActivityCompat.requestPermissions(this, new String[]
                            {Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }
    }

    public void populatePins(final Location cur){
        db.dbRef.child("pins").addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for(DataSnapshot ds : dataSnapshot.getChildren()){
                            if(ds.hasChildren()){
                                double lat = ds.child("locationCoordinate/latitude").getValue(double.class);
                                double lng = ds.child("locationCoordinate/longitude").getValue(double.class);
                                Location loc = new Location(LocationManager.GPS_PROVIDER);
                                loc.setLatitude(lat);
                                loc.setLongitude(lng);
                                if(cur.distanceTo(loc) < distanceRange){
                                    LatLng pin = new LatLng(loc.getLatitude(),loc.getLongitude());
                                    mMap.addMarker(new MarkerOptions().position(pin));
                                }
                            }
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.d("ERROR",databaseError.toString());
                    }
                }
        );
    }

    /**
     * Starts the intent for users to log in or sign up
     */
    public void loginSignup(View view) {
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .build(),
                RC_SIGN_IN);
    }

    /**
     * Logs out the user
     */
    public void logOut() {
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        Log.d("Authentication", "User successfully logged out");
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == RESULT_OK) {
                // Successfully signed in
                Log.d("Authentication", "User successfully logged in as: "
                        + auth.getCurrentUser().getDisplayName());
                auth.addCurrentUserToDatabase();
            } else {
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                // ...
                Log.e("Authentication", "Log in failed with error code: "
                        + response.getError().getErrorCode());
            }
        }
    }

    /**
     * Add listener for events that occur when menu icon is interacted with
     */
    private void addMenuIconListener() {
        mDrawerLayout.addDrawerListener(
                new DrawerLayout.DrawerListener() {
                    @Override
                    public void onDrawerSlide(View drawerView, float slideOffset) {
                        // Respond when the drawer's position changes
                    }

                    @Override
                    public void onDrawerOpened(View drawerView) {
                        // Respond when the drawer is opened
                        // e.g. make map background darker
                    }

                    @Override
                    public void onDrawerClosed(View drawerView) {
                        // Respond when the drawer is closed
                    }

                    @Override
                    public void onDrawerStateChanged(int newState) {
                        // Respond when the drawer motion state changes
                    }
                }
        );
    }

    /**
     * Add listener for the list items in the navigation menu.
     */
    private void addNavigationListener() {
        final NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        // set item as selected to persist highlight
                        menuItem.setChecked(true);
                        // close drawer when item is tapped
                        mDrawerLayout.closeDrawers();

                        int itemId = menuItem.getItemId();
                        switch (itemId) {
                            case R.id.login_signup:
                                loginSignup(navigationView);
                                break;
                            case R.id.logout:
                                logOut();
                                break;
                            case R.id.create_event:
                                //TODO
                                break;
                        }

                        return true;
                    }
                });
    }

    /**
     * When the home (hamburger menu) icon is selected, opens the navigation menu.
     *
     * @param item - list item in the navigation menu
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
