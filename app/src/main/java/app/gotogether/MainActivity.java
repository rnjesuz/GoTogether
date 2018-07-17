package app.gotogether;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends  AppCompatActivity {

    private FloatingActionMenu menuEvent;

    private List<FloatingActionMenu> menus = new ArrayList<>();
    private Handler mUiHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        menuEvent = (FloatingActionMenu) findViewById(R.id.menu_event);

        final FloatingActionButton actionCreate = (FloatingActionButton) findViewById(R.id.event_create);
        actionCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CreateEvent(view);
            }
        });

        final FloatingActionButton actionJoin = (FloatingActionButton) findViewById(R.id.event_join);
        actionJoin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createJoinPopUpWindow();
            }
        });

        menuEvent.setClosedOnTouchOutside(true);
        menuEvent.hideMenuButton(false);

        menus.add(menuEvent);

        int delay = 750;
            mUiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    menuEvent.showMenuButton(true);
                }
            }, delay);
    }

    // Onclick() of Create Event button
    public void CreateEvent(View view){
        Intent intent = new Intent(MainActivity.this, CreateEventActivity.class);
        startActivity(intent);
    }

    private void createJoinPopUpWindow() {
        ConstraintLayout mConstraintLayout = (ConstraintLayout) findViewById(R.id.MainActivityCL);
        PopupWindow cPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        View customView = inflater.inflate(R.layout.join_activity_input_window, null);

                /*
                    public PopupWindow (View contentView, int width, int height)
                        Create a new non focusable popup window which can display the contentView.
                        The dimension of the window must be passed to this constructor.

                        The popup does not provide any background. This should be handled by
                        the content view.

                    Parameters1
                        contentView : the popup's content
                        width : the popup's width
                        height : the popup's height
                */
        // Initialize a new instance of popup window
        cPopupWindow = new PopupWindow(
                customView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        // Set an elevation value for popup window
        // Call requires API level 21
        if (Build.VERSION.SDK_INT >= 21) {
            cPopupWindow.setElevation(5.0f);
        }

        // Find widgets inside "view".
        final EditText identifier = (EditText) customView.findViewById(R.id.activity_identifier_et);

        // Get a reference for the custom view confirm button
        Button confirmButton = (Button) customView.findViewById(R.id.activity_identifier_confirm);
        // Set a click listener for the popup window confirm button
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Restore activity to opaque
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
                // Dismiss the popup window
                cPopupWindow.dismiss();
                // Confirm Event
                MainActivity.this.LaunchEvent(identifier.getText().toString());

            }
        });

        // Get a reference for the custom view cancel button
        Button cancelButton = (Button) customView.findViewById(R.id.activity_identifier_cancel);
        // Set a click listener for the popup window cancel button
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Restore activity to opaque
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
                // Dismiss the popup window
                cPopupWindow.dismiss();
            }
        });

        // Listener for outside click - needed to aplly dim correction
        /*cPopupWindow.setTouchInterceptor(new View.OnTouchListener()
        {

            public boolean onTouch(View v, MotionEvent event)
            {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE)
                {
                    // Restore activity to opaque
                    ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                    clearDim(root);
                    Log.i("Outside", "plim");
                    // Dismiss the popup window
                    //cPopupWindow.dismiss();
                    return true;
                }

                return false;
            }
        });*/

        // Allow the popup to be focusable to edit text
        cPopupWindow.setFocusable(true);
        // Detect a click outside the window - Dismiss is the default behaviour of outside click
        cPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
            }
        });


        // Finally, show the popup window at the center location of root relative layout
        cPopupWindow.showAtLocation(mConstraintLayout, Gravity.CENTER, 0, 0);


        cPopupWindow.update();

        // Dim the activity
        ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
        applyDim(root, 0.8f);

    }

    private void LaunchEvent(String identifier) {

        //String eventIdentifier;
        //EditText identifierText = (EditText) alert.findViewById(R.id.activity_identifier_et);
        //Editable editable = identifierText.getText();
        //eventIdentifier = editable.toString();


        Intent intent = new Intent(MainActivity.this, JoinEventActivity.class);
        // For testing purposes only. TODO Remove!!!
        if(identifier.equals("teste")) {
            intent.putExtra("Destination", createJoinActivityDestinationBundle());
            intent.putExtra("Participants", createJoinActivityParticipantsBundle());
            startActivity(intent);
        }
        else{
            // Show Toast to inform of incorrect identifier
            Toast.makeText(this, "The provided identifier is an invalid one. Please try again.", Toast.LENGTH_SHORT).show();
        }

        // Get data from server
        // TODO
        // Generate Intent with destination and participants
        // TODO

        //startActivity(intent);
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

    /** Apply dim to the activity */
    public void applyDim(@NonNull ViewGroup parent, float dimAmount) {
        Drawable dim = new ColorDrawable(Color.BLACK);
        dim.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        dim.setAlpha((int) (255 * dimAmount));

        ViewGroupOverlay overlay = parent.getOverlay();
        overlay.add(dim);
    }

    /** Clear dim from the activity */
    public void clearDim(@NonNull ViewGroup parent) {
        ViewGroupOverlay overlay = parent.getOverlay();
        overlay.clear();
    }
}
