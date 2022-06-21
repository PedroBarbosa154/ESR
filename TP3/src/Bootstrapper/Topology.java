package Bootstrapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Topology {
    private Map<String, List<String>> overlay;  // Key: node name, Value: list of neighbors IPs
    private List<String> required_nodes;

    public Topology(Topology topology) {
        this.overlay = topology.getOverlay();
        this.required_nodes = topology.getRequiredNodes();
    }

    public Topology(String topology_filename) {
        this.overlay = new HashMap<>();
        this.required_nodes = new ArrayList<>();

        List<String> lines = new ArrayList<>();
        try {
            lines = Files.readAllLines(Paths.get(topology_filename), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String line : lines) {
            String[] splitted_line = line.split(":",2);
            switch (splitted_line[0]) {
                case "overlay":
                    parseOverlay(splitted_line[1]);
                    break;
                case "required_nodes":
                    parseRequiredNodes(splitted_line[1]);
                    break;
                default:
                    break;
            }
        }
    }

    public void parseOverlay(String input) {
        // split into key and value
        String[] split = input.split("-", 2);

        // parse key
        String node_name = split[0];

        // parse value
        String[] split2 = split[1].split(",");
        List<String> node_neighbors = new ArrayList<>(Arrays.asList(split2));

        this.overlay.put(node_name, node_neighbors);
    }

    public void parseRequiredNodes(String input) {
        String[] split = input.split(",");
        this.required_nodes = new ArrayList<>(Arrays.asList(split));
    }

    public Map<String, List<String>> getOverlay() {
        return this.overlay.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<String> getRequiredNodes() {
        return new ArrayList<>(this.required_nodes);
    }

    public void removeRequiredNode(String ip) {
        this.required_nodes.remove(ip);
    }

    public Topology clone() {
        return new Topology(this);
    }

}

