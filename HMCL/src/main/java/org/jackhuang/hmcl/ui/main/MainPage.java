/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.GameItem;
import org.jackhuang.hmcl.ui.versions.GameListPopupMenu;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.download.RemoteVersion.Type.RELEASE;
import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.SINE;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MainPage extends StackPane implements DecoratorPage {
    private static final String ANNOUNCEMENT = "announcement";

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();

    private final StringProperty currentGame = new SimpleStringProperty(this, "currentGame");
    private final ObservableList<Version> versions = FXCollections.observableArrayList();
    private Profile profile;

    private final JFXButton menuButton;

    {
        HBox titleNode = new HBox(8);
        titleNode.setPadding(new Insets(0, 0, 0, 2));
        titleNode.setAlignment(Pos.CENTER_LEFT);

        ImageView titleIcon = new ImageView(FXUtils.newBuiltinImage("/assets/img/icon-title.png"));
        Label titleLabel = new Label(Metadata.FULL_TITLE);
        if (I18n.isUpsideDown()) {
            titleIcon.setRotate(180);
            titleLabel.setRotate(180);
        }
        titleLabel.getStyleClass().add("jfx-decorator-title");
        titleLabel.textFillProperty().bind(Themes.titleFillProperty());
        titleNode.getChildren().setAll(titleIcon, titleLabel);

        state.setValue(new State(null, titleNode, false, false, true));

        setPadding(new Insets(20));

        HBox launchPane = new HBox();
        launchPane.getStyleClass().add("launch-pane");
        FXUtils.onScroll(launchPane, versions, list -> {
            String currentId = getCurrentGame();
            return Lang.indexWhere(list, instance -> instance.getId().equals(currentId));
        }, it -> profile.setSelectedVersion(it.getId()));

        StackPane.setAlignment(launchPane, Pos.BOTTOM_RIGHT);
        {
            JFXButton launchButton = new JFXButton();
            launchButton.getStyleClass().add("launch-button");
            launchButton.setDefaultButton(true);
            {
                VBox graphic = new VBox();
                graphic.setAlignment(Pos.CENTER);
                Label launchLabel = new Label();
                launchLabel.setStyle("-fx-font-size: 16px;");
                Label currentLabel = new Label();
                currentLabel.setStyle("-fx-font-size: 12px;");

                FXUtils.onChangeAndOperate(currentGameProperty(), new Consumer<>() {
                    private Tooltip tooltip;

                    @Override
                    public void accept(String currentGame) {
                        if (currentGame == null) {
                            launchLabel.setText(i18n("version.launch.empty"));
                            currentLabel.setText(null);
                            graphic.getChildren().setAll(launchLabel);
                            launchButton.setOnAction(e -> MainPage.this.launchNoGame());
                            if (tooltip == null)
                                tooltip = new Tooltip(i18n("version.launch.empty.tooltip"));
                            FXUtils.installFastTooltip(launchButton, tooltip);
                        } else {
                            launchLabel.setText(i18n("version.launch"));
                            currentLabel.setText(currentGame);
                            graphic.getChildren().setAll(launchLabel, currentLabel);
                            launchButton.setOnAction(e -> MainPage.this.launch());
                            if (tooltip != null)
                                Tooltip.uninstall(launchButton, tooltip);
                        }
                    }
                });

                launchButton.setGraphic(graphic);
            }

            menuButton = new JFXButton();
            menuButton.getStyleClass().add("menu-button");
            menuButton.setOnAction(e -> onMenu());
            FXUtils.installFastTooltip(menuButton, i18n("version.switch"));
            menuButton.setGraphic(SVG.ARROW_DROP_UP.createIcon(30));

            EventHandler<MouseEvent> secondaryClickHandle = event -> {
                if (event.getButton() == MouseButton.SECONDARY && event.getClickCount() == 1) {
                    menuButton.fire();
                    event.consume();
                }
            };
            launchButton.addEventHandler(MouseEvent.MOUSE_CLICKED, secondaryClickHandle);
            menuButton.addEventHandler(MouseEvent.MOUSE_CLICKED, secondaryClickHandle);

            launchPane.getChildren().setAll(launchButton, menuButton);
        }

        getChildren().addAll(launchPane);

    }
    private void launch() {
        Profile profile = Profiles.getSelectedProfile();
        Versions.launch(profile, profile.getSelectedVersion());
    }

    private void launchNoGame() {
        DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();
        VersionList<?> versionList = downloadProvider.getVersionListById("game");

        Holder<String> gameVersionHolder = new Holder<>();
        Task<?> task = versionList.refreshAsync("")
                .thenSupplyAsync(() -> versionList.getVersions("").stream()
                        .filter(it -> it.getVersionType() == RELEASE)
                        .sorted()
                        .findFirst()
                        .orElseThrow(() -> new IOException("No versions found")))
                .thenComposeAsync(version -> {
                    Profile profile = Profiles.getSelectedProfile();
                    DefaultDependencyManager dependency = profile.getDependency();
                    String gameVersion = gameVersionHolder.value = version.getGameVersion();

                    return dependency.gameBuilder()
                            .name(gameVersion)
                            .gameVersion(gameVersion)
                            .buildAsync();
                })
                .whenComplete(any -> profile.getRepository().refreshVersions())
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception == null) {
                        profile.setSelectedVersion(gameVersionHolder.value);
                        launch();
                    } else if (exception instanceof CancellationException) {
                        Controllers.showToast(i18n("message.cancelled"));
                    } else {
                        LOG.warning("Failed to install game", exception);
                        Controllers.dialog(StringUtils.getStackTrace(exception),
                                i18n("install.failed"),
                                MessageDialogPane.MessageType.WARNING);
                    }
                });
        Controllers.taskDialog(task, i18n("version.launch.empty.installing"), TaskCancellationAction.NORMAL);
    }

    private void onMenu() {
        GameListPopupMenu menu = new GameListPopupMenu();
        menu.getItems().setAll(versions.stream().map(it -> new GameItem(profile, it.getId())).toList());
        JFXPopup popup = new JFXPopup(menu);
        popup.show(
                menuButton,
                JFXPopup.PopupVPosition.BOTTOM,
                JFXPopup.PopupHPosition.RIGHT,
                0,
                -menuButton.getHeight()
        );
    }

    @Override
    public ReadOnlyObjectWrapper<State> stateProperty() {
        return state;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getCurrentGame() {
        return currentGame.get();
    }

    public StringProperty currentGameProperty() {
        return currentGame;
    }

    public void setCurrentGame(String currentGame) {
        this.currentGame.set(currentGame);
    }

    public ObservableList<Version> getVersions() {
        return versions;
    }

    public void initVersions(Profile profile, List<Version> versions) {
        FXUtils.checkFxUserThread();
        this.profile = profile;
        this.versions.setAll(versions);
    }
}
