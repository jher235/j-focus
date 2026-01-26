package com.jher235.jfocus;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "jfocus", mixinStandardHelpOptions = true, version = "1.0",
    description = "Java code context extractor for LLMs")
public class JFocusCli implements Callable<Integer> {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JFocusCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        System.out.println("🚀 J-Focus is ready. Use options to extract code.");
        return 0;
    }

}
