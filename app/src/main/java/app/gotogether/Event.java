package app.gotogether;

class Event {

    private String title;
    private String destination;
    private int participants;
    private String image;

    public Event(String title, String destination, int participants, String img){
        this.title = title;
        this.destination = destination;
        this.participants = participants;
        this .image = img;
    }

    public String getTitle() {
        return title;
    }

    public String getDestination() {
        return destination;
    }

    public int getParticipants() {
        return participants;
    }

    public String getImage() {
        return image;
    }
}
