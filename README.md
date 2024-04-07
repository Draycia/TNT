The NORI Tool (TNT)
===================

A program designed to Extract and Create NORI files.
Part of the Libre Trickster project.

Test it on any `.nri` or `.bac` file you want. If it isn't extracted correctly,
open a GitHub issue immediately and it will get fixed.

<pre>
Please avoid forking this repo unless you plan to make pull request.
Download the repo or a release if you want a local copy.
Non-updated forks are annoying. BTW, Followers > Stars > Watchers > Forks
</pre>

------------------------------------

How to compile and package TNT
----------------------------------

Install the Java JDK, links: [here](http://jdk.java.net) or [here](https://github.com/ojdkbuild/ojdkbuild)
Clone the project.

Access the `TNT` folder from the command prompt or terminal.

Then run the following command:
```bash
.\gradlew build
```

The build output will be in `build\libs`

Now you can copy & paste TNT.jar anywhere you like and use it from there.

To use TNT or find out the available commands for it, you can run it like so:
```bash
java -jar TNT.jar mode /path/to/file.nri
```
