    @Override
    protected void init() {
        super.init();

        String oldText = (searchBox != null) ? searchBox.getValue() : "";
        boolean wasFocused = (searchBox != null) && searchBox.isFocused();
        int oldCursor = (searchBox != null) ? searchBox.getCursorPosition() : 0;
        int oldHighlight = getSearchBoxHighlight();

        clearWidgets();
        rightPanelButtons.clear();

        searchBox = new GuideEditBox(font, 10, 10, 104, 14, Component.translatable("guide.search.placeholder"));
        searchBox.setValue(oldText);
        searchBox.setFocused(wasFocused);
        searchBox.setCursorPosition(oldCursor);
        setSearchBoxHighlight(oldHighlight);

        if (!oldText.isEmpty()) {
            searchBox.setSuggestion("");
        }

        searchBox.setResponder(text -> {
            searchDebounceTicks = 4;
            if (!text.isEmpty()) {
                searchBox.setSuggestion("");
            } else {
                searchBox.setSuggestion(Component.translatable("guide.search.placeholder").getString());
            }
            renderer.scrollOffset = 0;
        });
        addRenderableWidget(searchBox);

        if (navigator.activeItemFilterOverride != null) {
            searchBox.setWidth(86);
            Button clearFilterBtn = Button.builder(Component.literal("§c×"), (btn) -> {
                navigator.activeItemFilterOverride = null;
                rebuildWidgets();
            }).bounds(98, 9, 16, 16).build();
            addRenderableWidget(clearFilterBtn);
        }

        navigator.loadChaptersFromIndexFile();
        generateMenuButtons();
        initControlButtons();
        initUtilityButtons();

        loadChapterContentDirectly();
        updateElementsVisibility();
        if (this.customCursor == 0L) {
            this.applyCustomCursor();
        }
    }

    private void initControlButtons() {
        int ctrlX = width - 160;
        int panelLeft = ctrlX + 10;
        int btnH = 16;
        int gap = 4;
        int bottomSafe = 8;

        int rotBtnY = height - bottomSafe - btnH;
        int layerBtnY = rotBtnY - btnH - gap;

        structurePanel.syncLayout(panelLeft, layerBtnY);

        addRightBtn(Button.builder(Component.translatable("guide.button.zoom_out"),
                        b -> structurePanel.zoomScale = Math.max(0.25f, structurePanel.zoomScale - 0.15f))
                .bounds(panelLeft, 10, 68, btnH).build());
        addRightBtn(Button.builder(Component.translatable("guide.button.zoom_in"),
                        b -> structurePanel.zoomScale = Math.min(1.4f, structurePanel.zoomScale + 0.15f))
                .bounds(panelLeft + 72, 10, 68, btnH).build());

        addRightBtn(Button.builder(Component.translatable("guide.button.layer_down"),
                        b -> { if (structurePanel.currentRenderLayer > -1) structurePanel.currentRenderLayer--; })
                .bounds(panelLeft, layerBtnY, 44, btnH).build());
        addRightBtn(Button.builder(Component.translatable("guide.button.layer_all"),
                        b -> structurePanel.currentRenderLayer = -1)
                .bounds(panelLeft + 48, layerBtnY, 44, btnH).build());
        addRightBtn(Button.builder(Component.translatable("guide.button.layer_up"),
                        b -> { if (structurePanel.currentRenderLayer < 16) structurePanel.currentRenderLayer++; })
                .bounds(panelLeft + 96, layerBtnY, 44, btnH).build());

        addRightBtn(Button.builder(Component.literal("◀"),
                        b -> { structurePanel.isAutoRotating = false; structurePanel.rotationY -= 15.0f; })
                .bounds(panelLeft, rotBtnY, 44, btnH).build());
        addRightBtn(Button.builder(Component.translatable("guide.button.rot_auto"),
                        b -> structurePanel.isAutoRotating = !structurePanel.isAutoRotating)
                .bounds(panelLeft + 48, rotBtnY, 44, btnH).build());
        addRightBtn(Button.builder(Component.literal("▶"),
                        b -> { structurePanel.isAutoRotating = false; structurePanel.rotationY += 15.0f; })
                .bounds(panelLeft + 96, rotBtnY, 44, btnH).build());
    }

    private void addRightBtn(Button b) { rightPanelButtons.add(b); addRenderableWidget(b); }

    private void initUtilityButtons() {
        int bottomRowY = height - 24;
        int projRowY = height - 64;

        projectionButton = Button.builder(Component.translatable("guide.button.projection"), btn -> {
            var tabs = structurePanel.getStructureTabs();
            int activeIndex = structurePanel.getActiveStructureIndex();

            ResourceLocation structureLoc = null;
            String displayName = "";

            if (!tabs.isEmpty() && activeIndex < tabs.size()) {
                var activeTab = tabs.get(activeIndex);
                structureLoc = activeTab.location();

                try {
                    for (java.lang.reflect.Field field : activeTab.getClass().getDeclaredFields()) {
                        if (field.getType() == String.class) {
                            field.setAccessible(true);
                            displayName = (String) field.get(activeTab);
                            break;
                        }
                    }
                } catch (Exception e) {
                    displayName = "";
                }
            } else {
                String chapterFile = getCurrentChapterFile();
                if (!chapterFile.isEmpty()) {
                    structureLoc = ResourceLocation.fromNamespaceAndPath(bookNamespace, "structures/" + chapterFile);
                    displayName = chapterFile;
                }
            }

            if (displayName == null || displayName.isEmpty() && structureLoc != null) {
                displayName = structureLoc.getPath().replace("structures/", "").replace("_", " ");
            }

            if (structureLoc != null) {
                PlacementProjector.setActiveStructure(structureLoc, displayName);
                this.onClose();
            }
        }).bounds(10, projRowY, 104, 16).build();
        addRenderableWidget(projectionButton);

        Component favFilterComp = navigator.isFavoritesFilterActive
                ? Component.translatable("guide.button.fav_filter_on")
                : Component.translatable("guide.button.fav_filter_off");

        Button btnFavoritesToggle = Button.builder(favFilterComp, btn -> {
            navigator.isFavoritesFilterActive = !navigator.isFavoritesFilterActive;
            rebuildWidgets();
        }).bounds(10, bottomRowY, 50, 16).build();
        addRenderableWidget(btnFavoritesToggle);

        String currentFile = (navigator.activeSubmenuFileOverride != null)
                ? navigator.activeSubmenuFileOverride
                : getCurrentChapterFile();

        Component favAddComp = navigator.isFavorite(currentFile)
                ? Component.translatable("guide.button.fav_add_on")
                : Component.translatable("guide.button.fav_add_off");

        Button btnAddToFavorites = Button.builder(favAddComp, btn -> {
            if (!currentFile.isEmpty()) {
                navigator.toggleFavorite(currentFile);
                rebuildWidgets();
            }
        }).bounds(64, bottomRowY, 50, 16).build();
        addRenderableWidget(btnAddToFavorites);
    }
