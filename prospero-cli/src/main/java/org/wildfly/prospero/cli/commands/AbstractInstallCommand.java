/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.cli.commands;

import org.wildfly.channel.Repository;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.RepositoryUtils;
import org.wildfly.prospero.api.TemporaryFilesManager;
import org.wildfly.prospero.api.exceptions.InvalidRepositoryArchiveException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ArgumentParsingException;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.RepositoryDefinition;
import org.wildfly.prospero.cli.commands.options.LocalRepoOptions;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class AbstractInstallCommand extends AbstractCommand {

    @CommandLine.ArgGroup(
            heading = "%nInstallation source:%n",
            exclusive = true,
            multiplicity = "1",
            order = 1
    )
    InstallCommand.FeaturePackOrDefinition featurePackOrDefinition;

    @CommandLine.Option(
            names = {CliConstants.CHANNELS, CliConstants.CHANNEL},
            paramLabel = CliConstants.CHANNEL_REFERENCE,
            order = 2,
            split = CliConstants.Others.VALUE_DELIMETER
    )
    List<String> channelCoordinates = new ArrayList<>();

    @CommandLine.Option(
            names = CliConstants.CHANNEL_MANIFEST,
            paramLabel = CliConstants.CHANNEL_MANIFEST_REFERENCE,
            split = CliConstants.Others.VALUE_DELIMETER, order = 3
    )
    List<String> manifestCoordinates = Collections.emptyList();

    @CommandLine.Option(
            names = CliConstants.REPOSITORIES,
            paramLabel = CliConstants.REPO_URL,
            split = CliConstants.Others.VALUE_DELIMETER, order = 4
    )
    List<String> remoteRepositories = new ArrayList<>();

    @CommandLine.ArgGroup(exclusive = true, order = 5, headingKey = "localRepoOptions.heading")
    LocalRepoOptions localRepoOptions = new LocalRepoOptions();

    @CommandLine.Option(
            names = CliConstants.OFFLINE,
            order = 6
    )
    Optional<Boolean> offline = Optional.empty();

    @CommandLine.Option(
            names = CliConstants.MAVEN_SETTINGS,
            order = 7
    )
    Optional<Path> mavenSettings = Optional.empty();

    @CommandLine.Option(
            names = CliConstants.FILTER_MANIFEST,
            order = 8
    )
    Optional<Path> filterManifest = Optional.empty();

    public AbstractInstallCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    protected MavenOptions getMavenOptions() throws ArgumentParsingException {
        final MavenOptions.Builder mavenOptions = localRepoOptions.toOptions();
        offline.map(mavenOptions::setOffline);
        mavenSettings.map(mavenOptions::setMavenSettingsPath);
        filterManifest.map(mavenOptions::setFilterManifest);
        return mavenOptions.build();
    }

    protected ProvisioningDefinition.Builder buildDefinition(TemporaryFilesManager temporaryFiles)
            throws ArgumentParsingException, InvalidRepositoryArchiveException {
        // When using --channel-manifest, repositories ARE part of the channel definition and must be persisted.
        // For --profile or --channels, repositories should be temporary overrides handled separately.
        final boolean usingManifests = !manifestCoordinates.isEmpty();

        if (usingManifests && !remoteRepositories.isEmpty()) {
            // For manifests, repositories define the channels, so they must be included
            final List<Repository> repositories = RepositoryUtils.unzipArchives(
                    RepositoryDefinition.from(remoteRepositories), temporaryFiles);
            return ProvisioningDefinition.builder()
                    .setFpl(featurePackOrDefinition.fpl.orElse(null))
                    .setProfile(featurePackOrDefinition.profile.orElse(null))
                    .setManifests(manifestCoordinates)
                    .setChannelCoordinates(channelCoordinates)
                    .setOverrideRepositories(repositories)
                    .setDefinitionFile(featurePackOrDefinition.definition.map(Path::toUri).orElse(null));
        } else {
            // For profiles/channels, repositories are handled as temporary overrides in InstallCommand
            return ProvisioningDefinition.builder()
                    .setFpl(featurePackOrDefinition.fpl.orElse(null))
                    .setProfile(featurePackOrDefinition.profile.orElse(null))
                    .setManifests(manifestCoordinates)
                    .setChannelCoordinates(channelCoordinates)
                    .setDefinitionFile(featurePackOrDefinition.definition.map(Path::toUri).orElse(null));
        }
    }

    /**
     * Returns the list of remote repositories to use as temporary overrides during installation.
     * These repositories will not be persisted in the installation metadata (except when using --channel-manifest).
     */
    protected List<String> getRemoteRepositories() {
        return remoteRepositories;
    }

    /**
     * Returns true if using --channel-manifest (which requires repositories to be part of the channel definition).
     */
    protected boolean isUsingManifests() {
        return !manifestCoordinates.isEmpty();
    }
}
