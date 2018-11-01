package app.gotogether;

import android.arch.lifecycle.Lifecycle;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.CoordinatorLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static app.gotogether.MainActivity.getLocationFromAddress;
import static app.gotogether.MainActivity.names;
import static app.gotogether.MainActivity.pickup;
import static app.gotogether.R.layout.event_layout;
import static com.firebase.ui.auth.AuthUI.getApplicationContext;

class EventArrayAdapter extends ArrayAdapter<Event> {

    private Context context;
    private List<Event> eventList;
    private FirebaseAuth auth = FirebaseAuth.getInstance();

    // Constructor
    public EventArrayAdapter(@NonNull Context context, int resource, @NonNull ArrayList<Event> objects) {
        super(context, resource, objects);

        this.context = context;
        this.eventList = objects;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public View getView(int position, View convertView, ViewGroup parent) {

        //get the property we are displaying
        Event event = eventList.get(position);

        //get the inflater and inflate the XML layout for each item
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
        assert inflater != null;
        View view = inflater.inflate(event_layout, null);

        TextView title = (TextView) view.findViewById(R.id.titleView);
        TextView destination = (TextView) view.findViewById(R.id.destinationView);
        TextView participantsView = (TextView) view.findViewById(R.id.participantsView);
        ImageView image = (ImageView) view.findViewById(R.id.eventImg);
        Button edit = (Button) view.findViewById(R.id.editButton);
        ImageButton options = (ImageButton) view.findViewById(R.id.options);

        //TODO
        title.setText(event.getTitle());

        //TODO
        destination.setText((String)event.getDestination().get("street"));

        //TODO
        participantsView.setText(String.format("%s participants", Integer.toString(event.getParticipants())));

        //display trimmed excerpt for description
        /*int descriptionLength = property.getDescription().length();
        if(descriptionLength >= 100){
            String descriptionTrim = property.getDescription().substring(0, 100) + "...";
            description.setText(descriptionTrim);
        }else{
            description.setText(property.getDescription());
        }*/

        //get the image associated with this property
        int imageID = context.getResources().getIdentifier(event.getImage(), "mipmap", context.getPackageName());
        image.setImageResource(imageID);

        edit.setOnClickListener(v -> {
            ArrayList<User> eventParticipants = event.getParticipantsList();
            User currentUser = null;
            for (Iterator<User> iterator = eventParticipants.iterator(); ((Iterator) iterator).hasNext(); ){
                User u = iterator.next();
                if (u.getId().equals(auth.getUid())){
                    currentUser = u;
                    iterator.remove();
                }
            }
            /*for (User user : eventParticipants){
                if (user.getId().equals(auth.getUid())){
                    currentUser = user;
                    eventParticipants.remove(user);
                }
            }*/
            if (event.getOwner().equals(auth.getUid())){
                // edit the event's original info
                Intent intent = new Intent(context, UpdateEventActivity.class);
                intent.putExtra("eventUID", event.getId());
                intent.putExtra("Owner", event.getOwner());
                intent.putExtra("Title", event.getTitle());
                intent.putExtra("Destination", (String) event.getDestination().get("street"));
                Bundle participantsBundle = new Bundle();
                participantsBundle.putParcelableArrayList("Participants", eventParticipants);
                intent.putExtra("Start", currentUser.getStartAddress());
                if (currentUser.isDriver()) {
                    intent.putExtra("Driver", true);
                    intent.putExtra("Seats", currentUser.getSeats());
                } else {
                    intent.putExtra("Driver", false);
                }
                participantsBundle.putParcelable("User", currentUser);
                intent.putExtra("Participants", participantsBundle);
                context.startActivity(intent);
            } else {
                // edit participation information
                Intent intent = new Intent(context, JoinEventActivity.class);
                // add the event UID
                intent.putExtra("eventUID", event.getId());
                // add the owner
                intent.putExtra("Owner", event.getOwner());
                // add the title
                intent.putExtra("Title", event.getTitle());
                // add the known start
                intent.putExtra("Start", currentUser.getStartAddress());
                // add the destination
                String destination1 = (String) event.getDestination().get("street");
                Bundle destinationBundle = new Bundle();
                GeoPoint destinationGP = (GeoPoint) event.getDestination().get("LatLng");
                LatLng destinationLatLng = new LatLng(destinationGP.getLatitude(), destinationGP.getLongitude());
                destinationBundle.putParcelable("destinationLatLng", destinationLatLng);
                destinationBundle.putString("destinationAddress", destination1);
                intent.putExtra("Destination", destinationBundle);
                // add the participants
                Bundle participantsBundle = new Bundle();
                participantsBundle.putParcelableArrayList("Participants", eventParticipants);
                // add the user
                /*User user;
                Random random = new Random();
                String name = names[random.nextInt(names.length)];
                Log.i("Participant Name",name);
                String addr = pickup[random.nextInt(pickup.length)];
                Log.i("Participant PickUp",addr);
                boolean driver = random.nextBoolean();
                int seats = 0;
                if (driver) {
                    seats = ThreadLocalRandom.current().nextInt(1, 6+1);
                    user = new User(name , addr, getLocationFromAddress(context, addr), seats );
                }
                else {
                    user = new User(name, addr, getLocationFromAddress(context, addr));
                }
                participantsBundle.putParcelable("User", user);*/
                intent.putExtra("Participants", participantsBundle);
                // start
                context.startActivity(intent);
            }
        });

        if (!event.getOwner().equals(auth.getUid())){
            options.setVisibility(View.GONE);
        } else {
            options.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(context, v);

                // This activity implements OnMenuItemClickListener
                popup.setOnMenuItemClickListener(item -> {
                    switch (item.getItemId()) {
                        case R.id.option_show_identifier:
                            showEventIdentifier(event.getId(), parent);
                            return true;
                        case R.id.option_conclude:
                            // concludeEvent();
                            return true;
                        default:
                            return false;
                    }
                });
                popup.inflate(R.menu.event_options_menu);
                popup.show();
            });
        }
        return view;
    }

    /**
     * Shows a PopUp window with the event's unique identifier
     * @param eventUID the event identifier to show
     * @param parent the root view where the adapter is inserted
     *
     */
    public void showEventIdentifier(String eventUID, ViewGroup parent) {
        PopupWindow tPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        View customView = inflater.inflate(R.layout.show_identifier_pop_up_window, null);

        // Initialize a new instance of popup window
        tPopupWindow = new PopupWindow(
                customView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        // Set an elevation value for popup window
        // Call requires API level 21
        if (Build.VERSION.SDK_INT >= 21) {
            tPopupWindow.setElevation(5.0f);
        }

        // Get a reference for the custom view text
        TextView messageText = (TextView) customView.findViewById(R.id.id_tv);
        // Set text
        messageText.setText(eventUID);

        // Get a reference for the custom view copy button
        ImageButton copyButton = (ImageButton) customView.findViewById(R.id.ib_copy);
        // Set a click listener for the popup window close button
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Copy to Clipboard
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Identifier", "Enter this identifier in Go-Together to join my Event!: " + eventUID);
                clipboard.setPrimaryClip(clip);
                // Confirm copy to user bia Toast
                Toast.makeText(context, "Identifier copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });

        // Get a reference for the custom view close button
        ImageButton closeButton = (ImageButton) customView.findViewById(R.id.ib_close);
        // Set a click listener for the popup window close button
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Restore activity to opaque
                ViewGroup root = (ViewGroup) parent.getRootView();
                clearDim(root);
                // Dismiss the popup window
                tPopupWindow.dismiss();
            }
        });

        // Detect a click outside the window - Dismiss is the default behaviour of outside click
        tPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                ViewGroup root = (ViewGroup) parent.getRootView();
                clearDim(root);
            }
        });

        // Finally, show the popup window at the center location of root relative layout
        tPopupWindow.showAtLocation(parent, Gravity.CENTER, 0, 0);

        // Dim the activity
        ViewGroup root = (ViewGroup) parent.getRootView();
        applyDim(root, 0.8f);
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
