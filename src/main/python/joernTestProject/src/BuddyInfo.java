package main.python.joernTestProject.src;
public class BuddyInfo {

    private String name;
    private String address;
    private String phone_num;

    public BuddyInfo(String name, String address, String phone_num) {
        this.name = name;
        this.address = address;
        this.phone_num = phone_num;
    }

    public String getName()
    {
        return name;
    }

    public static void main(String[] args)
    {
        BuddyInfo friend1 = new BuddyInfo("Homer", "Carleton", "613");
        System.out.println ("Hello " + friend1.getName());
    }
}

