package gq.luma.bot.utils;

import gq.luma.bot.services.Service;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

public class WordEncoder implements Service {

    public static final int MAX_VALUE = 0xFFFFFF;
    private static String[] keyWords;

    @Override
    public void startService() throws Exception {
        try(InputStream is = WordEncoder.class.getResourceAsStream("/words.txt")){
            keyWords = new String(is.readAllBytes()).split("\r\n");
        }
        System.out.println("Loaded " + keyWords.length + " words.");
    }

    public static void main(String[] args) throws Exception {
        new URL("https://board.iverb.me/getDemo?id=79120").openConnection().getHeaderFields().forEach((s, l) -> System.out.println(s + ": " + Arrays.toString(l.toArray())));
        new WordEncoder().startService();
        int num = 1234;
        String encodedString = encode(num);
        System.out.println(num);
        System.out.println(encodedString);
        System.out.println(decode(encodedString));
    }

    public static String encode(int input){
        String significantBinary = pad(Integer.toBinaryString(input), 32).substring(8);
        System.out.println("SignificantBinary: " + significantBinary);
        String[] donezoed = Stream.of(splitInto(significantBinary, 6)).map(s -> keyWords[Integer.parseInt(s, 2)]).toArray(String[]::new);
        return String.join("", donezoed);
    }

    public static int decode(String input){
        String[] words = breakByCapital(input);
        return Integer.parseInt(String.join("", Stream.of(words).mapToInt(WordEncoder::findIntInKeys).mapToObj(Integer::toBinaryString).map(s -> pad(s, 6)).toArray(String[]::new)), 2);
    }

    public static String[] splitInto(String input, int length){
        String[] ret = new String[input.length()/length];
        for(int i = 0; i < input.length(); i += length){
            ret[i/length] = input.substring(i, i + length);
        }
        return ret;
    }

    private static String[] breakByCapital(String input){
        ArrayList<String> finalStrings = new ArrayList<>();
        StringBuilder accumulator = new StringBuilder();
        for(char c : input.toCharArray()){
            if(Character.isUpperCase(c)){
                if(accumulator.length() > 0) {
                    finalStrings.add(accumulator.toString());
                    accumulator = new StringBuilder();
                }
            }
            accumulator.append(c);
        }
        if(accumulator.length() > 0){
            finalStrings.add(accumulator.toString());
        }
        return finalStrings.toArray(new String[finalStrings.size()]);
    }

    private static int findIntInKeys(String s){
        for(int i = 0; i < keyWords.length; i++){
            if(s.equalsIgnoreCase(keyWords[i])){
                return i;
            }
        }
        return -1;
    }

    private static String pad(String s, int finalLength){
        while(s.length() < finalLength){
            s = "0" + s;
        }
        return s;
    }
}
