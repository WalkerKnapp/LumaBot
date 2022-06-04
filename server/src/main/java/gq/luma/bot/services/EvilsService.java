package gq.luma.bot.services;

import gq.luma.bot.Luma;
import inet.ipaddr.Address;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EvilsService implements Service {

    private static final Path evilsRoot = Path.of("evils");

    private Git ejrvRepo;
    private IPv4AddressTrie ejrvIpv4s;
    private IPv6AddressTrie ejrvIpv6s;

    private Git fireholRepo;
    private final HashMap<String, IPv4AddressTrie> fireholIpv4s = new HashMap<>();
    private final HashMap<String, IPv6AddressTrie> fireholIpv6s = new HashMap<>();

    private Git x4bNetRepo;
    private IPv4AddressTrie x4bNetIpv4s;

    private Git jhassineRepo;
    private IPv4AddressTrie jhassineIpv4s;

    @Override
    public void startService() throws Exception {
        this.ejrvRepo = openOrClone("https://github.com/ejrv/VPNs.git", "ejrv");
        this.fireholRepo = openOrClone("https://github.com/firehol/blocklist-ipsets.git", "firehol");
        this.x4bNetRepo = openOrClone("https://github.com/X4BNet/lists_vpn.git", "x4bnet");
        this.jhassineRepo = openOrClone("https://github.com/jhassine/server-ip-addresses.git", "jhassine");

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
            // Update ejrv lists
            this.ejrvRepo.fetch().call();
            this.ejrvRepo.pull().call();
            Path ejrvRoot = this.ejrvRepo.getRepository().getDirectory().toPath();

            this.ejrvIpv4s = this.readV4Tree(Files.lines(ejrvRoot.resolve("vpn-ipv4.txt")).iterator());
            this.ejrvIpv6s = this.readV6Tree(Files.lines(ejrvRoot.resolve("vpn-ipv6.txt")).iterator());

            // Update firehol lists
            this.fireholRepo.fetch().call();
            this.fireholRepo.pull().call();
            Path fireholRoot = this.fireholRepo.getRepository().getDirectory().toPath();

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
            Path x4bNetRoot = this.x4bNetRepo.getRepository().getDirectory().toPath();

            this.x4bNetIpv4s = this.readV4Tree(Files.lines(x4bNetRoot.resolve("ipv4.txt")).iterator());

            // Update jhassine
            this.jhassineRepo.fetch().call();
            this.jhassineRepo.pull().call();
            Path jhassineRoot = this.jhassineRepo.getRepository().getDirectory().toPath();

            this.jhassineIpv4s = this.readV4Tree(Files.lines(jhassineRoot.resolve("data/datacenters.txt")).iterator())

        } catch (GitAPIException | IOException e) {
            e.printStackTrace();
        }
    }

    private IPv4AddressTrie readV4Tree(Iterator<String> ipStream) {
        IPv4AddressTrie tree = new IPv4AddressTrie();

        while (ipStream.hasNext()) {
            String line = ipStream.next();

            if (line.charAt(0) != '#' && !line.isBlank()) {
                tree.add(new IPAddressString(line).getAddress().toIPv4());
            }
        }

        return tree;
    }

    private IPv6AddressTrie readV6Tree(Iterator<String> ipStream) {
        IPv6AddressTrie tree = new IPv6AddressTrie();

        while (ipStream.hasNext()) {
            String line = ipStream.next();

            if (line.charAt(0) != '#' && !line.isBlank()) {
                tree.add(new IPAddressString(line).getAddress().toIPv6());
            }
        }

        return tree;
    }

    public List<String> checkEvil(String given) {
        IPAddress address = new IPAddressString(given).getAddress();

        ArrayList<String> evilSources = new ArrayList<>();

        if (address.isIPv4()) {
            // Check ejrv
            if (ejrvIpv4s.elementContains(address.toIPv4())) {
                evilSources.add("ejrv - IPv4 VPNs and Datacenters");
            }
            // Check firehol
            for (var entry : this.fireholIpv4s.entrySet()) {
                if (entry.getValue().elementContains(address.toIPv4())) {
                    evilSources.add("firehol - " + entry.getKey());
                }
            }
            // Check x4bnet
            if (this.x4bNetIpv4s.elementContains(address.toIPv4())) {
                evilSources.add("x4bnet - IPv4 VPNs and Datacenters");
            }
            // Check jhassine
            if (this.jhassineIpv4s.elementContains(address.toIPv4())) {
                evilSources.add("jhassine - Datacenters");
            }
        } else if (address.isIPv6()) {
            // Check ejrv
            if (ejrvIpv6s.elementContains(address.toIPv6())) {
                evilSources.add("ejrv - IPv6 VPNs and Datacenters");
            }
            // Check firehol
            for (var entry : this.fireholIpv6s.entrySet()) {
                if (entry.getValue().elementContains(address.toIPv6())) {
                    evilSources.add("firehol - " + entry.getKey());
                }
            }
        } else {
            System.err.println("IP Address is neither v4 or v6: " + address);
        }

        return evilSources;
    }
}
