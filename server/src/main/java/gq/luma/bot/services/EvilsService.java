package gq.luma.bot.services;

import com.google.common.net.InternetDomainName;
import gq.luma.bot.Luma;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.format.util.AddressTrie;
import inet.ipaddr.ipv4.IPv4Address;
import inet.ipaddr.ipv4.IPv4AddressAssociativeTrie;
import inet.ipaddr.ipv4.IPv4AddressTrie;
import inet.ipaddr.ipv6.IPv6AddressTrie;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Address;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EvilsService implements Service {
    private static final Logger logger = LoggerFactory.getLogger(EvilsService.class);
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("\\W(www\\.)?(?<domain>[\\w\\-]*\\.[\\w.]+)\\W");

    private static final Path evilsRoot = Path.of("evils");

    private Git fireholRepo;
    private final HashMap<String, IPv4AddressTrie> fireholIpv4s = new HashMap<>();
    private final HashMap<String, IPv6AddressTrie> fireholIpv6s = new HashMap<>();

    private Git x4bNetRepo;
    private IPv4AddressTrie x4bNetIpv4s;

    private Git jhassineRepo;
    private IPv4AddressTrie jhassineIpv4s;

    private Git linuxclarkRepo;
    private Set<String> linuxclarkDomains;

    @Override
    public void startService() throws Exception {
        this.fireholRepo = openOrClone("https://github.com/firehol/blocklist-ipsets.git", "firehol");
        this.x4bNetRepo = openOrClone("https://github.com/X4BNet/lists_vpn.git", "x4bnet");
        this.jhassineRepo = openOrClone("https://github.com/jhassine/server-ip-addresses.git", "jhassine");
        this.linuxclarkRepo = openOrClone("https://github.com/linuxclark/web-hosting-companies.git", "linuxclark");

        this.update();

        Luma.schedulerService.scheduleAtFixedRate(this::update, 1, 1, TimeUnit.HOURS);
    }

    private Git openOrClone(String repo, String name) throws GitAPIException {
        try {
            return Git.open(evilsRoot.resolve(name).toFile());
        } catch (IOException e) {
            return Git.cloneRepository()
                    .setURI(repo)
                    .setDirectory(evilsRoot.resolve(name).toFile())
                    .call();
        }
    }

    private void update() {
        try {
            // Update firehol lists
            this.fireholRepo.fetch().call();
            this.fireholRepo.pull().call();
            Path fireholRoot = this.fireholRepo.getRepository().getDirectory().toPath().getParent();

            Files.list(fireholRoot)
                    .filter(path -> path.getFileName().toString().endsWith(".ipset"))
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        try {
                            // Determine if list is v4 or v6
                            Iterator<String> list = Files.lines(path).skip(3).iterator();
                            String detLine = list.next();

                            if (detLine.startsWith("# ipv4")) {
                                this.fireholIpv4s.put(path.getFileName().toString().replace(".ipset", ""),
                                        this.readV4Tree(list));
                            } else if (detLine.startsWith("# ipv6")) {
                                this.fireholIpv6s.put(path.getFileName().toString().replace(".ipset", ""),
                                        this.readV6Tree(list));
                            } else {
                                throw new IllegalStateException("Illegal determinant line in file " + path.getFileName().toString() + ": " + detLine);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

            // Update x4bnet
            this.x4bNetRepo.fetch().call();
            this.x4bNetRepo.pull().call();
            Path x4bNetRoot = this.x4bNetRepo.getRepository().getDirectory().toPath().getParent();

            this.x4bNetIpv4s = this.readV4Tree(Files.lines(x4bNetRoot.resolve("ipv4.txt")).iterator());

            // Update jhassine
            this.jhassineRepo.fetch().call();
            this.jhassineRepo.pull().call();
            Path jhassineRoot = this.jhassineRepo.getRepository().getDirectory().toPath().getParent();

            this.jhassineIpv4s = this.readV4Tree(Files.lines(jhassineRoot.resolve("data/datacenters.txt")).iterator());

            // Update linuxclark
            this.linuxclarkRepo.fetch().call();
            this.linuxclarkRepo.pull().call();
            Path linuxclarkRoot = this.linuxclarkRepo.getRepository().getDirectory().toPath().getParent();

            HashSet<String> linuxclarkDomains = new HashSet<>();
            Matcher m = DOMAIN_PATTERN.matcher(Files.readString(linuxclarkRoot.resolve("README.md")));

            while (m.find()) {
                linuxclarkDomains.add(m.group("domain").toLowerCase());
            }

            this.linuxclarkDomains = linuxclarkDomains;

        } catch (GitAPIException | IOException e) {
            e.printStackTrace();
        }
    }

    private IPv4AddressTrie readV4Tree(Iterator<String> ipStream) {
        IPv4AddressTrie tree = new IPv4AddressTrie();

        while (ipStream.hasNext()) {
            String line = ipStream.next();

            if (!line.isBlank() && line.charAt(0) != '#') {
                tree.add(new IPAddressString(line).getAddress().toIPv4());
            }
        }

        return tree;
    }

    private IPv6AddressTrie readV6Tree(Iterator<String> ipStream) {
        IPv6AddressTrie tree = new IPv6AddressTrie();

        while (ipStream.hasNext()) {
            String line = ipStream.next();

            if (!line.isBlank() && line.charAt(0) != '#') {
                tree.add(new IPAddressString(line).getAddress().toIPv6());
            }
        }

        return tree;
    }

    public List<String> checkEvil(String given) {
        IPAddress address = new IPAddressString(given).getAddress();

        if (address == null) return List.of();

        ArrayList<String> evilSources = new ArrayList<>();

        if (address.isIPv4()) {
            // Check firehol
            try {
                for (var entry : this.fireholIpv4s.entrySet()) {
                    if (entry.getValue().elementContains(address.toIPv4())) {
                        evilSources.add("firehol - " + entry.getKey());
                    }
                }
            } catch (Throwable t) {
                logger.error("Couldn't check ip against firehol ipv4", t);
            }
            // Check x4bnet
            try {
                if (this.x4bNetIpv4s.elementContains(address.toIPv4())) {
                    evilSources.add("x4bnet - IPv4 VPNs and Datacenters");
                }
            } catch (Throwable t) {
                logger.error("Couldn't check ip against x4bnet", t);
            }
            // Check jhassine
            try {
                if (this.jhassineIpv4s.elementContains(address.toIPv4())) {
                    evilSources.add("jhassine - Datacenters");
                }
            } catch (Throwable t) {
                logger.error("Couldn't check ip against jhassine", t);
            }
        } else if (address.isIPv6()) {
            // Check firehol
            try {
                for (var entry : this.fireholIpv6s.entrySet()) {
                    if (entry.getValue().elementContains(address.toIPv6())) {
                        evilSources.add("firehol - " + entry.getKey());
                    }
                }
            } catch (Throwable t) {
                logger.error("Couldn't check ip against firehol ipv6", t);
            }
        } else {
            logger.error("IP Address is neither v4 or v6: " + address);
        }

        try {
            InetAddress inetAddress = InetAddress.getByName(given);
            String hostName = Address.getHostName(inetAddress);

            InternetDomainName topDomain = InternetDomainName.from(hostName).topDomainUnderRegistrySuffix();

            // Check linuxclark
            if (this.linuxclarkDomains.contains(topDomain.toString().toLowerCase())) {
                evilSources.add("linuxclark - Cloud Hosts - " + topDomain.toString().toLowerCase());
            }

        } catch (UnknownHostException | IllegalStateException e) {
            if (!e.getMessage().contains(".arpa")) {
                logger.error("Couldn't resolve ip " + given + " to a host name:", e);
            }
        } catch (Throwable t) {
            logger.error("Couldn't check ip against domain filters", t);
        }

        return evilSources;
    }

    public static void main(String[] args) throws UnknownHostException {
        System.out.println(InternetDomainName.from(Address.getHostName(InetAddress.getByName("216.131.72.162"))).topDomainUnderRegistrySuffix());
    }
}
