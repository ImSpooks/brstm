# Brstm Player
This is an application build to play BRSTM/BFSTM audio files in Java

**Note:** *This is a fork from hackforyourlife's <a href="https://github.com/hackyourlife/brstm">brstm player</a>*

`.brstm` files are not included included.

## Usage

Using this simple Brstm Player is pretty simple.

Example:

```java
public class Test {
    
    public static void main(String[] args) {
        // creates the brstm object using random acces file
        BRSTM brstm = new BRSTM(new RandomAccesFile("test_file.brstm", "r"));
        // or an input stream for example
        brstm = new BRSTM(Test.class.getResourceAsStream("test_tile.brstm"));
        
        // creates the brstm player instance
        BrstmPlayer player = new BrstmPlayer(brstm);
        // starts the brstm player
        player.start();

    }
}
```
The Brstm Player currently supports the following functions: 
- Starting: `player.start()`
- Stopping: `player.stop()`
- Pausing: `player.pause()`
- Resuming: `player.resume()`
- Volume control: `player.setVolume(0.5F);`