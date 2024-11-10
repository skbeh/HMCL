/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.main;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.scene.control.ToggleGroup;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.Locales;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.selectedItemPropertyFor;

public final class SettingsPage extends SettingsView {

    private InvalidationListener updateListener;

    public SettingsPage() {
        FXUtils.smoothScrolling(scroll);

        // ==== Languages ====
        cboLanguage.getItems().setAll(Locales.LOCALES);
        selectedItemPropertyFor(cboLanguage).bindBidirectional(config().localizationProperty());
        // ====

        fileCommonLocation.selectedDataProperty().bindBidirectional(config().commonDirTypeProperty());
        fileCommonLocationSublist.subtitleProperty().bind(
                Bindings.createObjectBinding(() -> Optional.ofNullable(Settings.instance().getCommonDirectory())
                                .orElse(i18n("launcher.cache_directory.disabled")),
                        config().commonDirectoryProperty(), config().commonDirTypeProperty()));
    }

    @Override
    protected void onExportLogs() {
        // We cannot determine which file is JUL using.
        // So we write all the logs to a new file.
        thread(() -> {
            Path logFile = Paths.get("hmcl-exported-logs-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")) + ".log").toAbsolutePath();

            LOG.info("Exporting logs to " + logFile);
            try (OutputStream output = Files.newOutputStream(logFile)) {
                LOG.exportLogs(output);
            } catch (IOException e) {
                Platform.runLater(() -> Controllers.dialog(i18n("settings.launcher.launcher_log.export.failed") + "\n" + StringUtils.getStackTrace(e), null, MessageType.ERROR));
                LOG.warning("Failed to export logs", e);
                return;
            }

            Platform.runLater(() -> Controllers.dialog(i18n("settings.launcher.launcher_log.export.success", logFile)));
            FXUtils.showFileInExplorer(logFile);
        });
    }

    @Override
    protected void clearCacheDirectory() {
        FileUtils.cleanDirectoryQuietly(new File(Settings.instance().getCommonDirectory(), "cache"));
    }
}
