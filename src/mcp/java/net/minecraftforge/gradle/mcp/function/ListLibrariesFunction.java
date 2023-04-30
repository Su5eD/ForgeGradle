/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp.function;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraftforge.gradle.common.util.BundlerUtils;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;

class ListLibrariesFunction implements MCPFunction {
    private static final Attributes.Name FORMAT = new Attributes.Name("Bundler-Format");
    private final int spec;

    ListLibrariesFunction(int spec) {
        this.spec = spec;
    }

    @Override
    public File execute(MCPEnvironment environment) {
        File output = (File) environment.getArguments().computeIfAbsent("output", (key) -> environment.getFile("libraries.txt"));
        File bundle = (File) environment.getArguments().get("bundle");
        if (bundle != null && this.spec < 3) {
            throw new IllegalArgumentException("Invalid MCP Config: Listing bundle libraries is only supported for MCPConfig spec 3 or higher, found spec: " + this.spec);
        }

        try {
            Set<String> artifacts = bundle == null
                    ? listDownloadJsonLibraries(environment)
                    : BundlerUtils.listBundleLibraries(bundle.toPath());

            Set<File> libraries = new HashSet<>();
            for (String artifact : artifacts) {
                File lib = MavenArtifactDownloader.gradle(environment.project, artifact, false);
                if (lib == null)
                    throw new RuntimeException("Could not resolve download: " + artifact);

                libraries.add(lib);
            }

            // Write the list
            if (output.exists())
                output.delete();
            output.getParentFile().mkdirs();
            output.createNewFile();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(output), StandardCharsets.UTF_8));
            for (File file : libraries) {
                writer.println("-e=" + file.getAbsolutePath());
            }
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return output;
    }

    private Set<String> listDownloadJsonLibraries(MCPEnvironment environment) throws IOException {
        Gson gson = new Gson();
        Reader reader = new FileReader(environment.getStepOutput("downloadJson"));
        JsonObject json = gson.fromJson(reader, JsonObject.class);
        reader.close();

        return Utils.listDownloadJsonLibraries(json);
    }
}
