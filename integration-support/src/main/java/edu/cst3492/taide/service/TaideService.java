package edu.cst3492.taide.service;

import java.util.function.Consumer;

public interface TaideService {
    TaAnswer ask(String question, CodeContext context) throws TaideException;
    void askStreaming(String question, CodeContext context, Consumer<String> onToken,
                      Consumer<TaAnswer> onComplete, Consumer<Throwable> onError);
}
