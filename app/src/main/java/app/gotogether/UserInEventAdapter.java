package app.gotogether;

import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class UserInEventAdapter extends RecyclerView.Adapter<UserInEventAdapter.ViewHolder> {
    private UserListener mListener;
    private List<User> mUserDescriptions;

    public UserInEventAdapter(List<User> items, UserListener listener) {
        mUserDescriptions = items;
        mListener = listener;
    }

    public void setListener(UserListener listener) {
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.description_layout_participant, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.setData(mUserDescriptions.get(position));
    }

    @Override
    public int getItemCount() {
        int size = 0;
        if (mUserDescriptions != null)
            size = mUserDescriptions.size();
        return size;
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private ConstraintLayout participantInfo;
        private TextView nameView;
        private TextView destinationView;
        private TextView driverView;
        private TextView seatsView;
        private User user;

        public ViewHolder(View view) {
            super(view);
            itemView.setOnClickListener(this);
            participantInfo = (ConstraintLayout) view.findViewById(R.id.participant_info);
            nameView = (TextView) view.findViewById(R.id.nameView);
            destinationView = (TextView) view.findViewById(R.id.destinationView);
            driverView = (TextView) view.findViewById(R.id.driverView);
            seatsView = (TextView) view.findViewById(R.id.seatsView);
        }

        public void setData(User user) {
            this.user = user;
            participantInfo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    EventActivity.goMarkerParticipant(user);
                }
            });
            nameView.setText(user.getUsername());
            destinationView.setText(new SpannableString(Html.fromHtml("<b>Destination: </b>"+ user.getStartAddress())));
            if (user.isDriver()){
                driverView.setText(new SpannableString(Html.fromHtml("<b>Driver: </b>Available")));
                seatsView.setText(new SpannableString(Html.fromHtml("<b>Empty seats: </b>"+ user.getSeats())));
            } else {
                driverView.setText(new SpannableString(Html.fromHtml("<b>Driver: </b>Not available")));
                seatsView.setVisibility(View.GONE);
            }
        }

        @Override
        public void onClick(View v) {
            if (mListener != null) {
                mListener.onItemClick(user);
            }
        }
    }

    public interface UserListener {
        void onItemClick(User ud);
    }
}