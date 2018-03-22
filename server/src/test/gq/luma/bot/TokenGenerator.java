package gq.luma.bot;

import java.security.SecureRandom;
import java.util.Base64;

public class TokenGenerator {
    public static void main(String[] args){
        SecureRandom random = new SecureRandom();
        byte[] gen = new byte[64];
        random.nextBytes(gen);
        System.out.println(Base64.getEncoder().encodeToString(gen));
    }
}
