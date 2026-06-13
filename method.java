    private void generateMenuButtons() {
        String query = searchBox.getValue().toLowerCase().trim();
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();

        var chapters = navigator.getChapters();
        var chapterFiles = navigator.getChapterFiles();
        var chapterNames = navigator.getChapterNames();

        if (navigator.currentChapterIndex >= chapterFiles.size()) {
            navigator.currentChapterIndex = Math.max(0, chapterFiles.size() - 1);
        }

        int availableHeight = height - TOP_RESERVED - BOTTOM_BUTTONS_HEIGHT;
        int maxButtonsPerPage = Math.min(MAX_BUTTONS_PER_PAGE, Math.max(5, availableHeight / BUTTON_SPACING));
        int buttonWidth = Math.min(104, SIDEBAR_WIDTH - 10);

        List<Runnable> totalMenuRegistry = new ArrayList<>();

        for (int i = 0; i < chapterFiles.size(); i++) {
            final int index = i;
            final String file = chapterFiles.get(i);
            final String visibleName = chapterNames.get(i);
            final ItemStack icon = chapters.get(i).icon();

            if (navigator.activeItemFilterOverride != null) {
                var boundChapters = GuideNavigator.getItemToChapterMap().get(navigator.activeItemFilterOverride);
                if (boundChapters == null || !boundChapters.contains(file)) continue;
            }

            if (navigator.isFavoritesFilterActive) {
                boolean chapterOrSubInFav = navigator.isFavorite(file);
                if (!chapterOrSubInFav) {
                    String mdPath = buildMdPath(lang, file);
                    ResourceLocation loc = ResourceLocation.parse(bookNamespace + ":" + mdPath);
                    var resOpt = Minecraft.getInstance().getResourceManager().getResource(loc);
                    if (resOpt.isPresent()) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resOpt.get().open(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.trim().startsWith("@submenu:")) {
                                    for (String sub : line.replace("@submenu:", "").trim().split(",")) {
                                        if (navigator.isFavorite(sub.trim())) { chapterOrSubInFav = true; break; }
                                    }
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
                if (!chapterOrSubInFav) continue;
            }

            String mdPath = buildMdPath(lang, file);
            ResourceLocation loc = ResourceLocation.parse(bookNamespace + ":" + mdPath);
            List<String> submenuItems = new ArrayList<>();

            var resOpt = Minecraft.getInstance().getResourceManager().getResource(loc);
            if (resOpt.isPresent()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resOpt.get().open(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().startsWith("@submenu:")) {
                            for (String sub : line.replace("@submenu:", "").trim().split(",")) {
                                String subFile = sub.trim();
                                if (!subFile.isEmpty()) submenuItems.add(subFile);
                            }
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
            boolean hasSubmenu = !submenuItems.isEmpty();

            List<String> matchingSubs = new ArrayList<>();
            for (String subFile : submenuItems) {
                String subName = subFile;
                int subIdx = chapterFiles.indexOf(subFile);
                if (subIdx != -1 && subIdx < chapterNames.size()) subName = chapterNames.get(subIdx);
                if (subName.toLowerCase().contains(query)) matchingSubs.add(subFile);
            }

            boolean chapterMatches = visibleName.toLowerCase().contains(query);
            boolean submenuMatches = !matchingSubs.isEmpty();
            if (!query.isEmpty() && !chapterMatches && !submenuMatches) continue;

            final String label = navigator.isFavorite(file) ? "§e★§r " + visibleName : visibleName;
            boolean isDirectlyActive = (index == navigator.currentChapterIndex && navigator.activeSubmenuFileOverride == null);
            boolean isParentOfOpenSub = java.util.Objects.equals(file, navigator.parentChapterOfFile);
            boolean isActive = isDirectlyActive || isParentOfOpenSub;

            totalMenuRegistry.add(() -> {
                ChapterButton menuButton = new ChapterButton(10, 0, buttonWidth, BUTTON_HEIGHT, Component.literal(label), icon, (btn) -> {
                    navigator.currentChapterIndex = index;
                    renderer.scrollOffset = 0;
                    structurePanel.currentRenderLayer = -1;
                    navigator.activeSubmenuFileOverride = null;
                    if (!hasSubmenu) navigator.parentChapterOfFile = null;
                    loadChapterContent();
                });
                menuButton.active = !isActive;
                addRenderableWidget(menuButton);
            });

            boolean shouldExpand = isActive || (!query.isEmpty() && submenuMatches);
            if (hasSubmenu && shouldExpand) {
                List<String> subsToShow = (query.isEmpty() || isActive) ? submenuItems : matchingSubs;
                for (String subFile : subsToShow) {
                    final String finalSubFile = subFile;
                    String subName = subFile;
                    int subIdx = chapterFiles.indexOf(subFile);
                    if (subIdx != -1 && subIdx < chapterNames.size()) subName = chapterNames.get(subIdx);

                    if (navigator.isFavoritesFilterActive && !navigator.isFavorite(subFile)) continue;
                    if (!isActive && !query.isEmpty() && !subName.toLowerCase().contains(query)) continue;

                    final String subLabel = navigator.isFavorite(subFile) ? "  §e★§r " + subName : "  • " + subName;
                    final boolean isSubActive = subFile.equals(navigator.activeSubmenuFileOverride);

                    totalMenuRegistry.add(() -> {
                        ChapterButton subBtn = new ChapterButton(10, 0, buttonWidth, BUTTON_HEIGHT, Component.literal(subLabel), ItemStack.EMPTY, (btn) -> {
                            renderer.scrollOffset = 0;
                            structurePanel.currentRenderLayer = -1;
                            loadSubmenuChapterContent(finalSubFile);
                        });
                        subBtn.active = !isSubActive;
                        addRenderableWidget(subBtn);
                    });
                }
            }
        }

        int totalItems = totalMenuRegistry.size();
        int calculatedPages = totalItems / maxButtonsPerPage;
        if (totalItems % maxButtonsPerPage != 0) calculatedPages++;
        if (calculatedPages == 0) calculatedPages = 1;
        final int maxPages = calculatedPages;

        if (currentMenuPage >= maxPages) currentMenuPage = maxPages - 1;
        if (currentMenuPage < 0) currentMenuPage = 0;
        this.actualMaxPages = maxPages;

        int startIndex = currentMenuPage * maxButtonsPerPage;
        int endIndex = Math.min(startIndex + maxButtonsPerPage, totalItems);
        int buttonY = TOP_RESERVED;
        int widgetsBefore = this.children().size();

        for (int k = startIndex; k < endIndex; k++) {
            totalMenuRegistry.get(k).run();
            if (this.children().size() > widgetsBefore) {
                var newWidget = this.children().get(this.children().size() - 1);
                if (newWidget instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    widget.setY(buttonY);
                }
                widgetsBefore = this.children().size();
            }
            buttonY += BUTTON_SPACING;
        }

        if (maxPages > 1) {
            int navY = height - 44;

            Button prevBtn = Button.builder(Component.literal("◀"), (btn) -> {
                if (currentMenuPage > 0) { currentMenuPage--; rebuildWidgets(); }
            }).bounds(10, navY, 16, 16).build();
            prevBtn.active = (currentMenuPage > 0);
            addRenderableWidget(prevBtn);

            Button nextBtn = Button.builder(Component.literal("▶"), (btn) -> {
                if (currentMenuPage < maxPages - 1) { currentMenuPage++; rebuildWidgets(); }
            }).bounds(98, navY, 16, 16).build();
            nextBtn.active = (currentMenuPage < maxPages - 1);
            addRenderableWidget(nextBtn);
        }
    }

    private static class ChapterButton extends Button {
        private final ItemStack icon;
        private static final int ICON_PAD = 4;
        private static final int TEXT_PAD_NO_ICON = 6;
        private float hoverTicks = 0.0f;

        public ChapterButton(int x, int y, int w, int h, Component msg, ItemStack icon, OnPress onPress) {
            super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
            this.icon = icon;
        }
