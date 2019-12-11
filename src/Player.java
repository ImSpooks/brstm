import org.hackyourlife.gcn.dsp.*;
import org.hackyourlife.gcn.dsp.player.BrstmPlayer;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Created by Nick on 14 nov. 2019.
 * Copyright © ImSpooks
 */
public class Player {

    private static BrstmPlayer brstmPlayer;

    public static void main(String[] args) {
        int track = -1;
        if(args.length < 1) {
            System.err.println("Usage: player FILE [track]");
            System.exit(1);
        }
        if(args.length > 1) {
            try {
                track = Integer.parseInt(args[1]);
            } catch(NumberFormatException e) {
                System.err.println("Invalid number: " + args[1]);
                System.exit(1);
            }
        }

        // thread for pausing/resuming or changing volume
        thread().start();

        try {
            String filename = args[0].replace("%20", " ");
            String filenameLeft = null;
            String filenameRight = null;
            int lext = filename.lastIndexOf('.');
            if(lext > 1) {
                char[] data = filename.toCharArray();
                char c = data[lext - 1];
                if(c == 'L') {
                    data[lext - 1] = 'R';
                    filenameLeft = filename;
                    filenameRight = new String(data);
                } else if(c == 'R') {
                    data[lext - 1] = 'L';
                    filenameLeft = new String(data);
                    filenameRight = filename;
                }
            }
            Stream stream;
            RandomAccessFile file = new RandomAccessFile(filename, "r");

            try {
                stream = new BRSTM(file);
            } catch(FileFormatException e) {
                try {
                    stream = new BFSTM(file);
                } catch(FileFormatException ex) {
                    try {
                        stream = new RS03(file);
                    } catch(FileFormatException exc) {
                        if(filenameLeft != null
                                && new File(filenameLeft).exists()
                                && new File(filenameRight).exists()) {
                            file.close();
                            RandomAccessFile left = new RandomAccessFile(filenameLeft, "r");
                            RandomAccessFile right = new RandomAccessFile(filenameRight, "r");
                            try {
                                stream = new DSP(left, right);
                            } catch(FileFormatException exce) {
                                left.close();
                                right.close();
                                file = new RandomAccessFile(filename, "r");
                                stream = new DSP(file);
                            }
                        } else
                            stream = new DSP(file);
                    }
                }
            }

            System.out.printf("%d Channels, %d Hz\n", stream.getChannels(), stream.getSampleRate());

            brstmPlayer = new BrstmPlayer(stream);
            brstmPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Thread thread() {
        return new Thread(() -> {
            List<String> exits = Arrays.asList("stop", "exit", "close");

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    String line = scanner.nextLine().trim();

                    if (exits.stream().anyMatch(line::contains)) {
                        brstmPlayer.stop();
                    }

                    if (brstmPlayer != null) {
                        if (line.equalsIgnoreCase("toggle")) {
                            if (brstmPlayer.isPaused()) {
                                System.out.println("Resuming...");
                                brstmPlayer.resume();
                            }
                            else {
                                System.out.println("Pausing...");
                                brstmPlayer.pause();
                            }
                        }
                        if (line.contains(" ")) {
                            String[] args = line.split(" ");
                            if (args[0].equalsIgnoreCase("volume")) {
                                try {
                                    // between 0 and 1
                                    float percentage = Float.parseFloat(args[1]);

                                    brstmPlayer.setVolume(percentage);
                                } catch (NumberFormatException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}