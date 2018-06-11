package app.gotogether;

import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainMenuActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /* My functions */

    // Onclick() of Create Event button
    public void CreateEvent(View view){
        Intent intent = new Intent(MainMenuActivity.this, CreateEventActivity.class);
        startActivity(intent);
    }

    public void JoinEvent(View view){
        // Get data from server
        // TODO
        // Generate Intent with destination and participants
        // TODO


        Intent intent = new Intent(MainMenuActivity.this, JoinEventActivity.class);
        // For testing purposes only. TODO Remove!!!
        intent.putExtra("Destination", createJoinActivityDestinationBundle());
        intent.putExtra("Participants", createJoinActivityParticipantsBundle());

        startActivity(intent);
    }

    public void ManageEvents(View view){
    }

    /** Get latitude and longitude from the address*/
    public LatLng getLocationFromAddress(Context context, String strAddress) {

        Geocoder coder = new Geocoder(context);
        List<Address> address;
        LatLng p1 = null;

        try {
            // May throw an IOException
            address = coder.getFromLocationName(strAddress, 5);
            if (address == null) {
                return null;
            }

            Address location = address.get(0);
            p1 = new LatLng(location.getLatitude(), location.getLongitude() );

        } catch (IOException ex) {

            ex.printStackTrace();
        }

        return p1;
    }

    public Bundle createJoinActivityDestinationBundle(){
        /*Bundle args = new Bundle();
        LatLng destinationLatLng = getLocationFromAddress(getApplicationContext(), "R. Cap. Salgueiro Maia, 2725-079 Algueirão- Mem Martins, Portugal");
        args.putParcelable("destinationLatLng", destinationLatLng);*/
        /*intent.putExtra("destination", args);
        intent.putExtra("destinationAddress", "R. Cap. Salgueiro Maia, 2725-079 Algueirão- Mem Martins, Portugal");
        startActivity(intent);*/
        Bundle destinationBundle = new Bundle();
        LatLng destinationLatLng = getLocationFromAddress(getApplicationContext(), "R. Cap. Salgueiro Maia, 2725-079 Algueirão- Mem Martins, Portugal");
        destinationBundle.putParcelable("destinationLatLng", destinationLatLng);
        destinationBundle.putString("destinationAddress", "R. Cap. Salgueiro Maia, 2725-079 Algueirão- Mem Martins, Portugal");
        return destinationBundle;
    }

    public Bundle createJoinActivityParticipantsBundle(){
        Bundle participantsBundle = new Bundle();
        ArrayList<User> participants = new ArrayList<User>();
        // Dummy user 1
        User participant1 = new User("Participant1", "Avenida da Républica, Lisboa, Portugal", getLocationFromAddress(getApplicationContext(), "Avenida da Républica, Lisboa, Portugal"), false, 6);
        // Dummy user 2
        User participant2 = new User("Participant2", "Instituto Superior Técnico", getLocationFromAddress(getApplicationContext(), "Instituto Superior Técnico"), true, 1);
        // Dummy user 3
        User participant3 = new User("Participant3", "Algualva-Cacém", getLocationFromAddress(getApplicationContext(), "Agualva-Cacém"), true, 10);

        participants.add(participant1);
        participants.add(participant2);
        participants.add(participant3);
        participantsBundle.putParcelableArrayList("Participants", participants);
        return participantsBundle;
    }
}
