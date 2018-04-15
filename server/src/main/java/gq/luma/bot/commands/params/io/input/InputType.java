package gq.luma.bot.commands.params.io.input;

public enum InputType {
    IMAGE("bmp", "dcx", "gif", "icc", "icns", "ico", "iptc", "pcx", "psd", "rgbe", "tiff", "wbmp", "png", "jpg", "jpeg", "tga", "cr2"),
    DEMO("dem"),
    VIDEO("avi", "mp4", "flv"),
    AUDIO("wav", "mp3", "aav", "ogg"),
    DOCUMENT("doc", "docx", "htm", "html", "pdf", "rtf", "ppt", "xml", "ott", "tex", "gdoc", "info"),
    EXECUTABLE("exe", "run", "bat", "apk", "jsk", "jar", "ipa", "bin", "sh", "cmd", "app", "vbscript", "ezt", "com"),
    COMPRESSED("7-zip", "zip", "rar", "tar", "split", "lzma", "iso", "hfs", "gzip", "cpio", "bzip2", "z", "arj", "chm", "lhz", "nsis", "deb", "rpm", "wim", "udf"),
    OTHER;

    String[] extensions;

    InputType(String... extensions){
        this.extensions = extensions;
    }

    public String[] getExtensions() {
        return extensions;
    }
}
