package gq.luma.bot;

import gq.luma.bot.systems.demorender.fs.FuseRenderFS;

import java.nio.file.Paths;

public class SrcDemoTest {
    public static void main(String[] args) throws Exception {
        //Bot.globalProperties.load(Bot.class.getResourceAsStream("/global.properties"));
        //System.out.println(SrcRenderTask.downloadSteamMap(SrcGame.PORTAL_2.getWorkshopDir(), "859481999370793395", SrcGame.PORTAL_2.getPublishingApp()).get());
        //System.out.println(SrcDemo.of(new File("H:\\Portal 2\\Rendering\\escapeaperture3_lp_5.dem")).getFirstPlaybackTick());
        /*Collection<File> unsortedFiles = FileUtilities.unzip(new File("H:\\Portal 2\\Rendering\\temp\\demo-1517147889790\\void"), new File("H:\\Portal 2\\Rendering\\temp\\demo-1517147889790\\cotnainer_ride_voidclip.zip"));
        List<File> demoFiles = unsortedFiles.stream().sorted(AlphanumComparator.FILE_COMPARATOR).collect(Collectors.toList());
        List<SrcDemo> srcDemos = new ArrayList<>();
        for(File f : demoFiles){
            srcDemos.add(SrcDemo.of(f));
        }

        System.out.println(String.join("\n", demoFiles.stream().map(File::getName).toArray(String[]::new)));*/

        /*new KeyReference().startService();
        Config.setProperty("jcifs.netbios.wins", BotReference.SMB_URL);
        Config.setProperty("jcifs.smb.client.username", "walker");
        Config.setProperty("jcifs.smb.client.password", KeyReference.smbPass);
        SmbFileOutputStream fos = new SmbFileOutputStream("smb://" + BotReference.SMB_URL + "/render/" + "meme.txt");

        fos.write("Hello!".getBytes());

        fos.close();*/

        //FSInterface.openDokany(null, null).join();
        //Thread.sleep(120000);
        new FuseRenderFS(null, null).mount(Paths.get("F:\\SteamLibrary\\steamapps\\common\\Portal 2\\portal2\\export"), true, false);
    }
}
