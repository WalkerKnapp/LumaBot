package gq.luma.bot;

import ru.serce.jnrfuse.FuseStubFS;

import java.nio.file.Paths;

public class FuseTester extends FuseStubFS {
    public static void main(String[] args) throws InterruptedException {
        FuseTester tester = new FuseTester();
        tester.mount(Paths.get("H:\\Portal 2\\Rendering\\temp\\demo-1519087333305\\mount"), false, true);
        Thread.sleep(5000);
        tester.umount();

        Thread.sleep(5000);
        FuseTester tester2 = new FuseTester();
        tester2.mount(Paths.get("H:\\Portal 2\\Rendering\\temp\\demo-1519087333305\\mount"), false, true);
        Thread.sleep(5000);
        tester2.umount();
    }
}
