package mezz.jei.gui.overlay;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.filter.IFilterTextSource;
import mezz.jei.gui.input.GuiTextFieldFilter;
import mezz.jei.gui.input.ICharTypedHandler;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IDragHandler;
import mezz.jei.gui.input.IDraggableIngredientInternal;
import mezz.jei.gui.input.IRecipeFocusSource;
import mezz.jei.common.input.IUserInputHandler;
import mezz.jei.common.input.MouseUtil;
import mezz.jei.gui.input.handlers.CombinedDragHandler;
import mezz.jei.common.input.handlers.CombinedInputHandler;
import mezz.jei.gui.input.handlers.NullDragHandler;
import mezz.jei.gui.input.handlers.NullInputHandler;
import mezz.jei.gui.input.handlers.ProxyDragHandler;
import mezz.jei.gui.input.handlers.ProxyInputHandler;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistoryOverlay;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource;
import mezz.jei.gui.overlay.ingredients.IIngredientListOverlayContents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class IngredientListOverlay implements IIngredientListOverlay, IRecipeFocusSource, ICharTypedHandler {
	private final IconButton configButton;
	private final IIngredientListOverlayContents contents;
	private final LookupHistoryOverlay lookupHistoryOverlay;
	private final IClientToggleState toggleState;
	private final IFilterTextSource filterTextSource;
	private final GuiTextFieldFilter searchField;
	private final IngredientListOverlayController controller;
	private boolean screenPropertiesDirty;

	public IngredientListOverlay(
		IIngredientGridSource ingredientGridSource,
		IFilterTextSource filterTextSource,
		IScreenHelper screenHelper,
		IIngredientListOverlayContents contents,
		LookupHistoryOverlay lookupHistoryOverlay,
		IIngredientGridConfig ingredientGridConfig,
		IClientConfig clientConfig,
		IClientToggleState toggleState,
		IInternalKeyMappings keyBindings
	) {
		GuiPropertiesCache<Screen> guiPropertiesCache = new GuiPropertiesCache<>(
			screen -> screenHelper.getGuiProperties(screen)
				.orElse(null)
		);
		this.contents = contents;
		this.lookupHistoryOverlay = lookupHistoryOverlay;
		this.toggleState = toggleState;
		this.filterTextSource = filterTextSource;

		this.searchField = new GuiTextFieldFilter(contents::isEmpty);
		this.configButton = new IconButton(new ConfigButtonController(this::isListDisplayed, toggleState, keyBindings));
		this.controller = IngredientListOverlayController.create(
			guiPropertiesCache,
			clientConfig,
			toggleState,
			keyBindings,
			filterTextSource,
			contents,
			contents,
			lookupHistoryOverlay,
			this.searchField,
			configButton::updateBounds
		);
		this.controller.init();
		this.searchField.setResponder(filterTextSource::setFilterText);

		ingredientGridSource.addSourceListChangedListener(this::markScreenPropertiesDirty);

		Internal.registerRuntimeListenerRemoval(clientConfig.searchBarPosition().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(clientConfig.lookupHistoryEnabled().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(clientConfig.maxLookupHistoryRows().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(clientConfig.lookupHistoryDisplaySide().addListener(v -> markScreenPropertiesDirty()));
		addGridConfigListeners(ingredientGridConfig);
	}

	@Override
	public boolean isListDisplayed() {
		updateScreenPropertiesIfDirty();
		return this.controller.isListDisplayed();
	}

	/** The ingredient grid is only shown while something is typed into the search bar. */
	private boolean isGridShown() {
		if (!isListDisplayed()) {
			return false;
		}
		String filterText = this.filterTextSource.getFilterText();
		return filterText != null && !filterText.isBlank();
	}

	private void markScreenPropertiesDirty() {
		this.screenPropertiesDirty = true;
	}

	private void addGridConfigListeners(IIngredientGridConfig gridConfig) {
		Internal.registerRuntimeListenerRemoval(gridConfig.maxColumns().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.maxRows().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.drawBackground().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.horizontalAlignment().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.verticalAlignment().addListener(v -> markScreenPropertiesDirty()));
		Internal.registerRuntimeListenerRemoval(gridConfig.navigationVisibility().addListener(v -> markScreenPropertiesDirty()));
	}

	private void updateScreenPropertiesIfDirty() {
		if (this.screenPropertiesDirty) {
			this.screenPropertiesDirty = false;
			Minecraft minecraft = Minecraft.getInstance();
			getScreenPropertiesUpdater()
				.updateScreen(minecraft.screen)
				.forceUpdate();
		}
	}

	public IScreenPropertiesUpdater getScreenPropertiesUpdater() {
		return this.controller.getScreenPropertiesUpdater();
	}

	public void drawScreen(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		drawBackground(guiGraphics);
		drawForeground(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
	}

	public void drawBackground(GuiGraphics guiGraphics) {
		if (isListDisplayed()) {
			this.searchField.drawBackground(guiGraphics);
		}
		if (isGridShown()) {
			this.contents.drawBackground(guiGraphics);
		}
		if (this.controller.hasValidScreen() && toggleState.isOverlayEnabled()) {
			this.lookupHistoryOverlay.drawBackground(guiGraphics);
		}
	}

	public void drawForeground(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		if (isListDisplayed()) {
			this.searchField.drawForeground(guiGraphics, mouseX, mouseY, partialTicks);
		}
		if (isGridShown()) {
			this.contents.drawForeground(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
		}
		if (this.controller.hasValidScreen()) {

		}
		if (this.controller.hasValidScreen() && toggleState.isOverlayEnabled()) {
			this.lookupHistoryOverlay.draw(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
		}
	}

	public void drawTooltips(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY) {
		updateScreenPropertiesIfDirty();
		if (isGridShown()) {
			this.contents.drawTooltips(minecraft, guiGraphics, mouseX, mouseY);
		}
		if (this.controller.hasValidScreen()) {
		}
		if (this.controller.hasValidScreen() && toggleState.isOverlayEnabled()) {
			this.lookupHistoryOverlay.drawTooltips(minecraft, guiGraphics, mouseX, mouseY);
		}
	}

	public void drawOnForeground(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		updateScreenPropertiesIfDirty();
		if (isGridShown()) {
			this.contents.drawOnForeground(guiGraphics, mouseX, mouseY);
		}
		this.lookupHistoryOverlay.drawOnForeground(guiGraphics, mouseX, mouseY);
	}

	public void tick() {
		this.configButton.tick();
		if (isGridShown()) {
			this.contents.tick();
		}
		if (this.controller.hasValidScreen() && toggleState.isOverlayEnabled()) {
			this.lookupHistoryOverlay.tick();
		}
	}

	@Override
	public Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
		updateScreenPropertiesIfDirty();
		if (isGridShown()) {
			return Stream.concat(this.contents.getIngredientUnderMouse(mouseX, mouseY), this.lookupHistoryOverlay.getIngredientUnderMouse(mouseX, mouseY));
		}
		if (this.lookupHistoryOverlay.isListDisplayed()) {
			return this.lookupHistoryOverlay.getIngredientUnderMouse(mouseX, mouseY);
		}
		return Stream.empty();
	}

	@Override
	public Stream<IDraggableIngredientInternal<?>> getDraggableIngredientUnderMouse(double mouseX, double mouseY) {
		updateScreenPropertiesIfDirty();
		if (isGridShown()) {
			return Stream.concat(this.contents.getDraggableIngredientUnderMouse(mouseX, mouseY), this.lookupHistoryOverlay.getDraggableIngredientUnderMouse(mouseX, mouseY));
		}
		if (this.lookupHistoryOverlay.isListDisplayed()) {
			return this.lookupHistoryOverlay.getDraggableIngredientUnderMouse(mouseX, mouseY);
		}
		return Stream.empty();
	}

	public IUserInputHandler createInputHandler() {
		final IUserInputHandler searchInputHandler = this.searchField.createInputHandler();
		final IUserInputHandler displayedInputHandler = new CombinedInputHandler(
			"IngredientListOverlay",
			searchInputHandler,
			this.contents.createInputHandler()
		);


		return new ProxyInputHandler(() -> {
			if (isGridShown()) {
				return displayedInputHandler;
			}
			if (isListDisplayed()) {
				return searchInputHandler;
			}
			return NullInputHandler.INSTANCE;
		});
	}

	public IUserInputHandler createDeleteItemInputHandler() {
		final IUserInputHandler deleteItemInputHandler = this.contents.createDeleteItemInputHandler();

		return new ProxyInputHandler(() -> {
			if (isGridShown()) {
				return deleteItemInputHandler;
			}
			return NullInputHandler.INSTANCE;
		});
	}

	public IDragHandler createDragHandler() {
		final IDragHandler combinedDragHandlers = new CombinedDragHandler(
			this.contents.createDragHandler(),
			this.lookupHistoryOverlay.createDragHandler()
		);

		return new ProxyDragHandler(() -> {
			if (isGridShown()) {
				return combinedDragHandlers;
			}
			return NullDragHandler.INSTANCE;
		});
	}

	@Override
	public boolean hasKeyboardFocus() {
		return isListDisplayed() && this.searchField.isFocused();
	}

	@Override
	public boolean onCharTyped(char codePoint, int modifiers) {
		return searchField.charTyped(codePoint, modifiers);
	}

	@Override
	public Optional<ITypedIngredient<?>> getIngredientUnderMouse() {
		if (isGridShown()) {
			double mouseX = MouseUtil.getX();
			double mouseY = MouseUtil.getY();
			return this.contents.getIngredientUnderMouse(mouseX, mouseY)
				.<ITypedIngredient<?>>map(IClickableIngredientInternal::getTypedIngredient)
				.findFirst();
		}
		return Optional.empty();
	}

	@Nullable
	@Override
	public <T> T getIngredientUnderMouse(IIngredientType<T> ingredientType) {
		if (isGridShown()) {
			double mouseX = MouseUtil.getX();
			double mouseY = MouseUtil.getY();
			return this.contents.getIngredientUnderMouse(mouseX, mouseY)
				.map(IClickableIngredientInternal::getTypedIngredient)
				.map(i -> i.getIngredient(ingredientType))
				.flatMap(Optional::stream)
				.findFirst()
				.orElse(null);
		}
		return null;
	}

	@Override
	public <T> List<T> getVisibleIngredients(IIngredientType<T> ingredientType) {
		updateScreenPropertiesIfDirty();
		if (isGridShown()) {
			return this.contents.getVisibleIngredients(ingredientType)
				.toList();
		}
		return Collections.emptyList();
	}
}
