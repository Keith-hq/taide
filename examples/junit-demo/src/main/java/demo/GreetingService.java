package demo;

public class GreetingService {
    public String greet(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name");
        }
        return "Hello " + name.strip();
    }
}
