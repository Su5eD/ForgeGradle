/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.common.config;

import net.minecraftforge.gradle.common.config.MCPConfigV1.Function;
import net.minecraftforge.gradle.common.util.Utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class UserdevConfigV2 extends UserdevConfigV1 {
    public static UserdevConfigV2 get(InputStream stream) {
        return Utils.fromJson(stream, UserdevConfigV2.class);
    }
    public static UserdevConfigV2 get(byte[] data) {
        return get(new ByteArrayInputStream(data));
    }

    public DataFunction processor;
    public String patchesOriginalPrefix;
    public String patchesModifiedPrefix;
    @Nullable
    private Boolean notchObf; //This is a Boolean so we can set to null and it won't be printed in the json.
    @Nullable
    private List<String> universalFilters;
    @Nullable
    private List<String> excludedReobfPackages;
    @Nullable
    public List<String> modules; // Modules passed to --module-path
    private String sourceFileCharset = StandardCharsets.UTF_8.name();
    private int javaRecompileTarget = 8;

    public void setNotchObf(boolean value) {
        this.notchObf = value ? true : null;
    }

    public boolean getNotchObf() {
        return this.notchObf != null && this.notchObf;
    }

    public void setSourceFileCharset(String value) {
        if (!Charset.isSupported(value)) {
            throw new IllegalArgumentException("Unsupported charset: " + value);
        }
        sourceFileCharset = value;
    }

    public String getSourceFileCharset() {
        return sourceFileCharset;
    }

    public void addUniversalFilter(String value) {
        if (universalFilters == null)
            universalFilters = new ArrayList<>();
        universalFilters.add(value);
    }

    public List<String> getUniversalFilters() {
        return universalFilters == null ? Collections.emptyList() : universalFilters;
    }

    public void addExcludedReobfPackage(String pkg) {
        if (excludedReobfPackages == null)
            excludedReobfPackages = new ArrayList<>();
        excludedReobfPackages.add(pkg);
    }

    public List<String> getExcludedReobfPackages() {
        return excludedReobfPackages == null ? Collections.emptyList() : excludedReobfPackages;
    }

    public void addModule(String value) {
        if (modules == null)
            modules = new ArrayList<>();
        modules.add(value);
    }

    @Nullable
    public List<String> getModules() {
        return modules;
    }

    public int getJavaRecompileTarget() {
        return this.javaRecompileTarget;
    }

    public void setJavaRecompileTarget(int javaRecompileTarget) {
        this.javaRecompileTarget = javaRecompileTarget;
    }

    public static class DataFunction extends Function {
        protected Map<String, String> data;

        public Map<String, String> getData() {
            return this.data == null ? Collections.emptyMap() : data;
        }

        @Nullable
        public String setData(String name, String path) {
            if (this.data == null)
                this.data = new HashMap<>();
            return this.data.put(name, path);
        }
    }
}
