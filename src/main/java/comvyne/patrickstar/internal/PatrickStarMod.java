package comvyne.patrickstar.internal;

import net.fabricmc.api.ModInitializer;

/**
 * Server-side event wiring for the API. The mod is loadable on both physical sides.
 */
public final class PatrickStarMod implements ModInitializer {
    @Override
    public void onInitialize() {
        PathDirector.initialize();
        // Diagnostic scenarios are opt-in and run only in the isolated dev server.
        if (java.nio.file.Files.isRegularFile(java.nio.file.Path.of("patrick-runtime-smoke.enabled"))) {
            PatrickRuntimeSmoke.initialize();
        }
        if (java.nio.file.Files.isRegularFile(java.nio.file.Path.of("patrick-consumer-integration.enabled"))) {
            PatrickConsumerIntegrationProbe.initialize();
        }
        if (java.nio.file.Files.isRegularFile(java.nio.file.Path.of("patrick-overload-runtime.enabled"))) {
            PatrickOverloadRuntimeProbe.initialize();
        }
    }
}
