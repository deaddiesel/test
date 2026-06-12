    private void loadChapterContentDirectly() {
        renderer.clear();
        structurePanel.clearTabs();
        StructureRenderer.resetCache();

        var chapterFiles = navigator.getChapterFiles();
        if (chapterFiles.isEmpty()) {
            renderer.rawTextLines.add("§c" + Component.translatable("guide.error.chapter_list_empty").getString());
            renderer.originalMarkdownLines.add("");
            return;
        }

        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        String activeFile = navigator.activeSubmenuFileOverride != null
                ? navigator.activeSubmenuFileOverride
                : chapterFiles.get(navigator.currentChapterIndex);

        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(bookNamespace, buildMdPath(lang, activeFile));
        if (Minecraft.getInstance().getResourceManager().getResource(loc).isEmpty()) {
            renderer.rawTextLines.add("§c" + Component.translatable("guide.error.file_not_found", activeFile).getString());
            renderer.originalMarkdownLines.add("");
            return;
        }

        List<MarkdownParser.ParsedLine> rawLines = MarkdownParser.parseMarkdownFile(loc);
        if (rawLines.isEmpty()) {
            renderer.rawTextLines.add("§c" + Component.translatable("guide.error.file_read").getString());
            renderer.originalMarkdownLines.add("");
            return;
        }

        String pendingTabName = null;
        int structureCount = 0;
        final int MAX_STRUCTURES = 3;

        for (MarkdownParser.ParsedLine p : rawLines) {
            String line = p.text();
            if (line == null) continue;

            if (line.startsWith("@tab:")) {
                pendingTabName = line.substring(5).trim();
            } else if (line.startsWith("@bind:")) {
                pendingTabName = null;
            } else if (line.startsWith("@structure:")) {
                if (structureCount >= MAX_STRUCTURES) {
                    pendingTabName = null;
                    continue;
                }
                String rawName = line.substring(11);
                if (rawName.startsWith(" ") || rawName.isEmpty()) {
                    pendingTabName = null;
                    continue;
                }

                String structName = rawName.trim();
                ResourceLocation structLoc = ResourceLocation.fromNamespaceAndPath(bookNamespace, "structures/" + structName);

                structureCount++;
                boolean hasCustomName = (pendingTabName != null && !pendingTabName.isEmpty());

                structurePanel.addStructureTab(new GuideModels.StructureTabEntry(structLoc, hasCustomName ? pendingTabName : "", hasCustomName));
                pendingTabName = null;
            } else if (line.startsWith("@matrix_craft:")) {
                renderer.originalMarkdownLines.add(line.trim());
                renderer.rawTextLines.add("@matrix_craft:");
                renderer.linkTargetsPerLine.add(new ArrayList<>());
            } else if (line.startsWith("@inline_item:")) {
                renderer.originalMarkdownLines.add(line.trim());
                renderer.rawTextLines.add("@inline_item:");
                renderer.linkTargetsPerLine.add(new ArrayList<>());
            } else if (line.startsWith("@image:")) {
                String imgData = line.substring(7).trim();
                String[] parts = imgData.split(",");
                String path = parts[0];
                int drawW = 150, drawH = 150;
                String align = "left";

                if (parts.length > 1) {
                    try {
                        drawW = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        align = parts[1].trim().toLowerCase();
                    }
                }
                if (parts.length > 2) {
                    try {
                        drawH = Integer.parseInt(parts[2].trim());
                    } catch (NumberFormatException e) {
                        align = parts[2].trim().toLowerCase();
                    }
                }
                if (parts.length > 3) {
                    align = parts[3].trim().toLowerCase();
                }

                renderer.imagesToRender.add(new GuideRenderer.ImageInfo(path, drawW, drawH, align));
                renderer.originalMarkdownLines.add(line.trim());
                renderer.rawTextLines.add("");
                renderer.linkTargetsPerLine.add(new ArrayList<>());
            } else if (line.startsWith("@gif:")) {
                String gifData = line.substring(5).trim();
                String[] parts = gifData.split(",");

                String path = parts[0];
                int drawW = 128, drawH = 128;
                String align = "center";

                if (parts.length > 1) {
                    try {
                        drawW = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        align = parts[1].trim().toLowerCase();
                    }
                }
                if (parts.length > 2) {
                    try {
                        drawH = Integer.parseInt(parts[2].trim());
                    } catch (NumberFormatException e) {
                        align = parts[2].trim().toLowerCase();
                    }
                }
                if (parts.length > 3) {
                    align = parts[3].trim().toLowerCase();
                }

                renderer.gifsToRender.add(new GuideRenderer.GifInfo(path, drawW, drawH, align));
                renderer.originalMarkdownLines.add(line.trim());
                renderer.rawTextLines.add("");
                renderer.linkTargetsPerLine.add(new ArrayList<>());
            } else if (line.startsWith("@mob:")) {
                String mobData = line.substring(5).trim();
                String[] parts = mobData.split(",");

                String entityId = parts[0];
                int drawW = 128, drawH = 128;
                String align = "center";

                if (parts.length > 1) {
                    try {
                        drawW = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        align = parts[1].trim().toLowerCase();
                    }
                }
                if (parts.length > 2) {
                    try {
                        drawH = Integer.parseInt(parts[2].trim());
                    } catch (NumberFormatException e) {
                        align = parts[2].trim().toLowerCase();
                    }
                }
                if (parts.length > 3) {
                    align = parts[3].trim().toLowerCase();
                }

                renderer.mobsToRender.add(new GuideRenderer.MobInfo(entityId, drawW, drawH, align));
                renderer.originalMarkdownLines.add(line.trim());
                renderer.rawTextLines.add("");
                renderer.linkTargetsPerLine.add(new ArrayList<>());
            } else if (line.startsWith("@item:")) {
                renderer.originalMarkdownLines.add(line.trim());
                renderer.rawTextLines.add("@item:");
                renderer.linkTargetsPerLine.add(new ArrayList<>());
            } else if (line.startsWith("@spoiler:") || line.equals("@endspoiler")) {
                renderer.originalMarkdownLines.add(line.trim());
                renderer.rawTextLines.add(line.trim());
                renderer.linkTargetsPerLine.add(new ArrayList<>());
            } else {
                String lineText = line.trim();
                if (lineText.isEmpty()) {
                    renderer.rawTextLines.add("");
                    renderer.originalMarkdownLines.add("");
                    renderer.linkTargetsPerLine.add(new ArrayList<>());
                } else {
                    String cleanLine = lineText.replaceAll("§.", "");
                    if (cleanLine.equals("---") || cleanLine.equals("***") || cleanLine.matches("^-{3,}$") || cleanLine.matches("^\\*{3,}$")) {
                        int colorHex = 0xFF3A3A3A;
                        boolean isBold = lineText.toLowerCase().contains("§l");

                        for (int pos = 0; pos < lineText.length() - 1; pos++) {
                            if (lineText.charAt(pos) == '§') {
                                char code = lineText.charAt(pos + 1);
                                net.minecraft.ChatFormatting formatting = net.minecraft.ChatFormatting.getByCode(code);
                                if (formatting != null && formatting.getColor() != null) {
                                    colorHex = 0xFF000000 | formatting.getColor();
                                    break;
                                }
                            }
                        }

                        String dividerMarker = "@divider:" + Integer.toHexString(colorHex) + ":" + isBold;
                        renderer.originalMarkdownLines.add(dividerMarker);
                        renderer.rawTextLines.add(dividerMarker);
                        renderer.linkTargetsPerLine.add(new ArrayList<>());
                    } else {
                        renderer.linkTargetsPerLine.add(renderer.extractLinkTargets(lineText));
                        renderer.originalMarkdownLines.add(lineText);
                        renderer.rawTextLines.add(renderer.processLinksForDisplay(lineText));
                    }
                }
            }
        }

        for (GuideRenderer.GifInfo gif : renderer.gifsToRender) {
            AnimatedGif animatedGif = new AnimatedGif();
            String gifPath = gif.texturePath();
            ResourceLocation gifLoc = ResourceLocation.tryParse(gifPath);
            if (gifLoc != null) {
                animatedGif.load(gifLoc);
                renderer.loadedGifs.add(animatedGif);
            }
        }

        for (GuideRenderer.MobInfo mob : renderer.mobsToRender) {
            MobEntityRenderer mobRenderer = new MobEntityRenderer();
            mobRenderer.setEntity(mob.entityId());
            renderer.loadedMobs.add(mobRenderer);
        }
        renderer.rebuildTextLines(width, height, !structurePanel.getStructureTabs().isEmpty());
    }
