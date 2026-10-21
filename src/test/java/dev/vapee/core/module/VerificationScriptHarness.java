package dev.vapee.core.module;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Runs the real wrapper in disposable project copies, never against operator data. */
public final class VerificationScriptHarness {
    public static void main(String[] args) throws Exception {
        String shell = System.getenv().getOrDefault("VAPEECORE_PWSH", "pwsh");
        Process process = new ProcessBuilder(shell, "-NoProfile", "-File",
                "scripts/tests/verification.Tests.ps1").redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        var result = Pattern.compile("(?m)^Verification script tests passed (\\d+) checks\\.\\r?$").matcher(output);
        if (exit != 0 || !result.find()) throw new AssertionError("Verification script tests failed: " + output);
        int checks = Integer.parseInt(result.group(1));
        if (checks <= 0 || result.find()) throw new AssertionError("Invalid script test receipt: " + output);
        System.out.println("VerificationScriptHarness passed " + checks + " checks.");
    }
}
