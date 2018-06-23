package com.ilummc.wayback.storage;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonParser;
import com.ilummc.tlib.resources.TLocale;
import com.ilummc.wayback.data.Breakpoint;
import com.ilummc.wayback.util.Files;
import com.ilummc.wayback.util.Jsons;
import com.ilummc.wayback.util.Pair;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocalStorage implements ConfigurationSerializable, Storage {

    private List<String> root = ImmutableList.of();

    public static LocalStorage valueOf(Map<String, Object> map) {
        return Jsons.mapTo(map, LocalStorage.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> serialize() {
        return new ObjectMapper().convertValue(this, Map.class);
    }

    @Override
    public boolean init() {
        root = root.stream().filter(s -> {
            try {
                new File(s).mkdirs();
                return true;
            } catch (SecurityException e) {
                TLocale.Logger.warn("FILE_LOCAL.SECURITY_DENIED");
                return false;
            }
        }).collect(Collectors.toList());
        return true;
    }

    @Override
    public long space() {
        return largest().getFreeSpace();
    }

    @Override
    public File createTempFile(File base, String suffix) throws IOException {
        return File.createTempFile("Wayback-" + UUID.randomUUID().toString(), suffix, base);
    }

    public List<File> list(String name) {
        return root.stream()
                .map(File::new)
                .filter(f -> f.isDirectory() && f.listFiles() != null)
                .flatMap(f -> Arrays.stream(Objects.requireNonNull(f.listFiles())))
                .filter(f -> f.getName().startsWith(name))
                .collect(Collectors.toList());
    }

    public Optional<String> latestName() {
        return latestZip().flatMap(file -> Optional.of(file.getName().substring(0, file.getName().lastIndexOf('.'))));
    }

    public Optional<File> latestZip() {
        return root.stream()
                .map(File::new)
                .filter(f -> f.isDirectory() && f.listFiles() != null)
                .flatMap(f -> Arrays.stream(Objects.requireNonNull(f.listFiles())))
                .filter(f -> f.getName().endsWith(".json"))
                .map(f -> Pair.of(f, LocalDateTime.parse(f.getName().replace('_', ':')
                        .substring(0, f.getName().lastIndexOf('.')))))
                .max(Comparator.comparing(Pair::getValue))
                .flatMap(pair -> Optional.of(pair.getKey()));
    }

    @Override
    public Optional<Breakpoint> findLast() {
        return latestZip().flatMap(file -> Optional.of(new JsonParser().parse(Files.toJson(file))))
                .flatMap(element -> Optional.of(Jsons.mapTo(element.getAsJsonObject(), Breakpoint.class)));
    }

    @Override
    public Optional<Breakpoint> findNearest(LocalDateTime time) {
        return root.stream()
                .map(File::new)
                .filter(f -> f.isDirectory() && f.listFiles() != null)
                .flatMap(f -> Arrays.stream(Objects.requireNonNull(f.listFiles())))
                .filter(f -> f.getName().endsWith(".json"))
                .map(f -> Pair.of(f, LocalDateTime.parse(f.getName().replace('_', ':')
                        .substring(0, f.getName().lastIndexOf('.')))))
                .min((o1, o2) -> ((int) Math.abs(time.toEpochSecond(ZoneOffset.MIN) - o1.getValue().toEpochSecond(ZoneOffset.MIN))
                        - ((int) Math.abs(time.toEpochSecond(ZoneOffset.MIN) - o2.getValue().toEpochSecond(ZoneOffset.MIN)))))
                .flatMap(pair -> Optional.of(Pair.of(new JsonParser().parse(Files.toJson(pair.getKey())).getAsJsonObject(), pair.getValue())))
                .flatMap(pair -> Optional.of(Jsons.mapTo(pair.getKey(), Breakpoint.class).setTime(pair.getValue())));
    }

    @Override
    public List<LocalDateTime> listAvailable() {
        return root.stream()
                .map(File::new)
                .filter(f -> f.isDirectory() && f.listFiles() != null)
                .flatMap(f -> Arrays.stream(Objects.requireNonNull(f.listFiles())))
                .filter(f -> f.getName().endsWith(".json"))
                .map(f -> LocalDateTime.parse(f.getName().replace('_', ':')
                        .substring(0, f.getName().lastIndexOf('.'))))
                .collect(Collectors.toList());
    }

    public Optional<File> findByTime(LocalDateTime time, String suffix) {
        String s = time.toString().replace(':', '_');
        return root.stream()
                .map(File::new)
                .filter(f -> f.isDirectory() && f.listFiles() != null)
                .flatMap(f -> Arrays.stream(Objects.requireNonNull(f.listFiles())))
                .filter(file -> file.getName().endsWith("." + suffix))
                .filter(file -> s.equals(file.getName().substring(0, file.getName().lastIndexOf('.'))))
                .findAny();
    }

    public Breakpoint getExactly(LocalDateTime time) {
        return findByTime(time, "json")
                .flatMap(file -> Optional.of(Jsons.mapTo(new JsonParser().parse(Files.toJson(file)).getAsJsonObject(), Breakpoint.class)
                        .setTime(time)))
                .orElse(null);
    }

    public File largest() {
        return root.stream().map(File::new).min((o1, o2) -> (int) (o1.getFreeSpace() - o2.getFreeSpace())).orElseGet(() -> {
            File file = File.listRoots()[0];
            File file1 = new File(file, "Wayback");
            file1.mkdirs();
            return file1;
        });
    }


}
