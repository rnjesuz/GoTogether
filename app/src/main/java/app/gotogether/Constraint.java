package app.gotogether;

public class Constraint {

    public String constraint;
    // to know if it's the final child of the layout
    public boolean isFinalChild = false;

    public Constraint(String contraint){
        this.constraint=contraint;
    }
}
