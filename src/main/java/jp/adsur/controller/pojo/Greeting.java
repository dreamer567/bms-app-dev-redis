package jp.adsur.controller.pojo;

public record Greeting(long id, String content) {

    public Greeting() {
        this(0L, null);
    }
}
