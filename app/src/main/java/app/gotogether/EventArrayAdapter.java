package app.gotogether;

import android.arch.lifecycle.Lifecycle;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static app.gotogether.MainActivity.getLocationFromAddress;
import static app.gotogether.MainActivity.names;
import static app.gotogether.MainActivity.pickup;
import static app.gotogether.R.layout.event_layout;

class EventArrayAdapter extends ArrayAdapter<Event> {

    private Context context;
    private List<Event> eventList;

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

        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // TODO criador? CreateEvent : JoinEvent

                Intent intent = new Intent(context, JoinEventActivity.class);
                // add the event UID
                intent.putExtra("eventUID", event.getId());
                // add the title
                intent.putExtra("Title", event.getTitle());
                // add the destination
                String destination = (String) event.getDestination().get("street");
                Bundle destinationBundle = new Bundle();
                // TODO this LatLng should be in the event, and thus, qwwe should use the getter
                LatLng destinationLatLng = getLocationFromAddress(context, destination);
                destinationBundle.putParcelable("destinationLatLng", destinationLatLng);
                destinationBundle.putString("destinationAddress", destination);
                intent.putExtra("Destination", destinationBundle);
                // add the participants
                Bundle participantsBundle = new Bundle();
                participantsBundle.putParcelableArrayList("Participants", event.getParticipantsList());
                // add the user
                User user;
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
                participantsBundle.putParcelable("User", user);
                intent.putExtra("Participants", participantsBundle);
                // start
                context.startActivity(intent);
            }
        });

        return view;
    }
}
