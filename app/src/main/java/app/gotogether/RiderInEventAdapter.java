package app.gotogether;

import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.List;

public class RiderInEventAdapter extends RecyclerView.Adapter<RiderInEventAdapter.ViewHolder> {
    private RiderListener mListener;
    private List<String> mRiderDescriptions;

    public RiderInEventAdapter(List<String> items, RiderListener listener) {
        mRiderDescriptions = items;
        mListener = listener;
    }

    public void setListener(RiderListener listener) {
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.description_layout_rider, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.setData(mRiderDescriptions.get(position));
    }

    @Override
    public int getItemCount() {
        int size = 0;
        if (mRiderDescriptions != null)
            size = mRiderDescriptions.size();
        return size;
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public TextView usernameView;
        public String username;

        public ViewHolder(View view) {
            super(view);
            itemView.setOnClickListener(this);
            usernameView = view.findViewById(R.id.riderView);
            }

        public void setData(String username) {
            this.username = username;
            usernameView.setText(username);
        }

        @Override
        public void onClick(View v) {
            if (mListener != null) {
                mListener.onItemClick(username);
            }
        }
    }

    public interface RiderListener {
        void onItemClick(String username);
    }
}