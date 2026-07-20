package de.shockbase.levelborderpvp.gui;

import de.shockbase.levelborderpvp.border.BorderService;
import de.shockbase.levelborderpvp.config.ConfigSchema;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.i18n.Messages;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public final class ConfigDialog {

    private static final int INPUT_WIDTH = 400;
    private static final int BUTTON_WIDTH = 120;
    private static final double MAX_SLIDER_STEPS = 10_000.0D;

    private final LevelBorderSettings settings;
    private final BorderService borderService;
    private final Messages messages;
    private final Predicate<CommandSender> canConfigure;

    public ConfigDialog(
            LevelBorderSettings settings,
            BorderService borderService,
            Messages messages,
            Predicate<CommandSender> canConfigure
    ) {
        this.settings = settings;
        this.borderService = borderService;
        this.messages = messages;
        this.canConfigure = canConfigure;
    }

    public void open(Player player) {
        if (!ensurePermission(player)) {
            return;
        }
        player.showDialog(mainDialog());
    }

    private Dialog mainDialog() {
        List<ActionButton> categoryButtons = new ArrayList<>();
        for (ConfigSchema.Category category : ConfigSchema.Category.values()) {
            categoryButtons.add(button(
                    messages.text(category.labelKey()),
                    messages.text(category.descriptionKey()),
                    audience -> {
                        if (audience instanceof Player player && ensurePermission(player)) {
                            player.showDialog(categoryDialog(category));
                        }
                    }
            ));
        }

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(messages.text("config-gui.title"), NamedTextColor.GOLD))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(
                                Component.text(messages.text("config-gui.intro"), NamedTextColor.GRAY),
                                INPUT_WIDTH
                        )))
                        .build())
                .type(DialogType.multiAction(categoryButtons, cancelButton(), 2))
        );
    }

    private Dialog categoryDialog(ConfigSchema.Category category) {
        List<InputBinding> bindings = createBindings(category);
        List<DialogInput> inputs = new ArrayList<>(bindings.size());
        for (InputBinding binding : bindings) {
            inputs.add(binding.input());
        }

        List<DialogBody> body = List.of(
                DialogBody.plainMessage(
                        Component.text(messages.text(category.descriptionKey()), NamedTextColor.GRAY),
                        INPUT_WIDTH
                ),
                DialogBody.plainMessage(
                        Component.text(messages.text("config-gui.input-hint"), NamedTextColor.DARK_GRAY),
                        INPUT_WIDTH
                )
        );

        ActionButton save = button(
                messages.text("config-gui.save"),
                messages.text("config-gui.save-description"),
                (response, audience) -> save(category, bindings, response, audience)
        );
        ActionButton back = button(
                messages.text("config-gui.back"),
                messages.text("config-gui.back-description"),
                audience -> {
                    if (audience instanceof Player player && ensurePermission(player)) {
                        player.showDialog(mainDialog());
                    }
                }
        );

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(messages.text(category.labelKey()), NamedTextColor.GOLD))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(List.of(save, back), cancelButton(), 2))
        );
    }

    private List<InputBinding> createBindings(ConfigSchema.Category category) {
        List<InputBinding> bindings = new ArrayList<>();
        int index = 0;
        for (ConfigSchema.Option option : ConfigSchema.options(category)) {
            String inputKey = "field_" + index++;
            Object currentValue = settings.configValue(option.path());
            Component label = optionLabel(option);

            if (option.type() == ConfigSchema.ValueType.BOOLEAN) {
                boolean initial = currentValue instanceof Boolean value
                        ? value
                        : Boolean.parseBoolean(String.valueOf(currentValue));
                bindings.add(new InputBinding(
                        option,
                        inputKey,
                        InputKind.BOOLEAN,
                        DialogInput.bool(inputKey, label).initial(initial).build()
                ));
                continue;
            }

            if (!option.allowedValues().isEmpty()) {
                String current = String.valueOf(currentValue);
                List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
                boolean hasInitial = false;
                for (String allowed : option.allowedValues()) {
                    boolean initial = allowed.equalsIgnoreCase(current);
                    hasInitial |= initial;
                    entries.add(SingleOptionDialogInput.OptionEntry.create(
                            allowed,
                            Component.text(localizedAllowedValue(allowed)),
                            initial
                    ));
                }
                if (!hasInitial && !entries.isEmpty()) {
                    SingleOptionDialogInput.OptionEntry first = entries.getFirst();
                    entries.set(0, SingleOptionDialogInput.OptionEntry.create(first.id(), first.display(), true));
                }
                bindings.add(new InputBinding(
                        option,
                        inputKey,
                        InputKind.SINGLE_OPTION,
                        DialogInput.singleOption(inputKey, label, entries)
                                .width(INPUT_WIDTH)
                                .labelVisible(true)
                                .build()
                ));
                continue;
            }

            Number currentNumber = currentValue instanceof Number number ? number : null;
            if (option.type().numeric() && canUseSlider(option, currentNumber)) {
                bindings.add(new InputBinding(
                        option,
                        inputKey,
                        InputKind.NUMBER_RANGE,
                        DialogInput.numberRange(
                                        inputKey,
                                        label,
                                        option.min().floatValue(),
                                        option.max().floatValue()
                                )
                                .width(INPUT_WIDTH)
                                .initial(currentNumber.floatValue())
                                .step(option.step().floatValue())
                                .labelFormat("%s: %s")
                                .build()
                ));
                continue;
            }

            String initial = ConfigSchema.formatForInput(option, currentValue);
            TextDialogInput.Builder textBuilder = DialogInput.text(inputKey, label)
                    .width(INPUT_WIDTH)
                    .labelVisible(true)
                    .initial(initial)
                    .maxLength(Math.max(1_024, initial.length()));
            if (option.type() == ConfigSchema.ValueType.STRING_LIST) {
                int lineCount = currentValue instanceof List<?> list ? list.size() : 4;
                textBuilder.multiline(TextDialogInput.MultilineOptions.create(
                        Math.max(4, Math.min(100, lineCount + 4)),
                        100
                ));
            }
            bindings.add(new InputBinding(option, inputKey, InputKind.TEXT, textBuilder.build()));
        }
        return List.copyOf(bindings);
    }

    private boolean canUseSlider(ConfigSchema.Option option, Number currentValue) {
        if (currentValue == null || option.min() == null || option.max() == null || option.step() == null) {
            return false;
        }
        double value = currentValue.doubleValue();
        double steps = (option.max() - option.min()) / option.step();
        return Double.isFinite(value)
                && value >= option.min()
                && value <= option.max()
                && steps > 0.0D
                && steps <= MAX_SLIDER_STEPS;
    }

    private void save(
            ConfigSchema.Category category,
            List<InputBinding> bindings,
            DialogResponseView response,
            Audience audience
    ) {
        if (!(audience instanceof Player player) || !ensurePermission(player)) {
            audience.closeDialog();
            return;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (InputBinding binding : bindings) {
            try {
                values.put(binding.option().path(), readValue(binding, response));
            } catch (ConfigSchema.ValidationException exception) {
                player.sendMessage(messages.text(
                        "config-gui.invalid",
                        Messages.placeholder("option", messages.text(binding.option().labelKey())),
                        Messages.placeholder("reason", validationMessage(exception))
                ));
                player.showDialog(categoryDialog(category));
                return;
            }
        }

        settings.setConfigValues(values);
        borderService.refreshRuntimeSettings();
        player.sendMessage(messages.text(
                "config-gui.saved",
                Messages.placeholder("category", messages.text(category.labelKey())),
                Messages.placeholder("count", values.size())
        ));
        player.showDialog(mainDialog());
    }

    private Object readValue(InputBinding binding, DialogResponseView response) {
        return switch (binding.kind()) {
            case BOOLEAN -> {
                Boolean value = response.getBoolean(binding.inputKey());
                if (value == null) {
                    throw ConfigSchema.ValidationException.of("missing");
                }
                yield value;
            }
            case NUMBER_RANGE -> ConfigSchema.parseNumber(binding.option(), response.getFloat(binding.inputKey()));
            case SINGLE_OPTION, TEXT -> ConfigSchema.parse(binding.option(), response.getText(binding.inputKey()));
        };
    }

    private String validationMessage(ConfigSchema.ValidationException exception) {
        return messages.text(
                "config-gui.validation." + exception.reason(),
                Messages.placeholder("min", exception.placeholders().getOrDefault("min", "")),
                Messages.placeholder("max", exception.placeholders().getOrDefault("max", "")),
                Messages.placeholder("allowed", exception.placeholders().getOrDefault("allowed", ""))
        );
    }

    private Component optionLabel(ConfigSchema.Option option) {
        return Component.text(messages.text(option.labelKey()), NamedTextColor.WHITE)
                .hoverEvent(Component.text(messages.text(option.descriptionKey()), NamedTextColor.GRAY));
    }

    private String localizedAllowedValue(String value) {
        String key = "config-gui.value." + value.toLowerCase(Locale.ROOT).replace('_', '-');
        String localized = messages.text(key);
        return key.equals(localized) ? value : localized;
    }

    private ActionButton cancelButton() {
        return button(
                messages.text("config-gui.cancel"),
                messages.text("config-gui.cancel-description"),
                Audience::closeDialog
        );
    }

    private ActionButton button(String label, String tooltip, java.util.function.Consumer<Audience> callback) {
        return button(label, tooltip, (response, audience) -> callback.accept(audience));
    }

    private ActionButton button(String label, String tooltip, DialogCallback callback) {
        return ActionButton.builder(Component.text(label))
                .tooltip(Component.text(tooltip))
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(
                        callback::accept,
                        ClickCallback.Options.builder().uses(1).build()
                ))
                .build();
    }

    private boolean ensurePermission(Player player) {
        if (canConfigure.test(player)) {
            return true;
        }
        player.closeDialog();
        player.sendMessage(messages.text(
                "command.no-permission",
                Messages.placeholder("permission", settings.commandPermission())
        ));
        return false;
    }

    private enum InputKind {
        BOOLEAN,
        NUMBER_RANGE,
        SINGLE_OPTION,
        TEXT
    }

    private record InputBinding(
            ConfigSchema.Option option,
            String inputKey,
            InputKind kind,
            DialogInput input
    ) {
    }

    @FunctionalInterface
    private interface DialogCallback {
        void accept(DialogResponseView response, Audience audience);
    }
}
