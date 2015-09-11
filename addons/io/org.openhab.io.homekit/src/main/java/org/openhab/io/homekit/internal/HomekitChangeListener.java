package org.openhab.io.homekit.internal;

import java.util.Collection;

import org.eclipse.smarthome.core.items.Item;
import org.eclipse.smarthome.core.items.ItemRegistry;
import org.eclipse.smarthome.core.items.ItemRegistryChangeListener;
import org.openhab.io.homekit.internal.accessories.HomekitAccessoryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beowulfe.hap.HomekitRoot;

/**
 * Listens for changes to the item registry. When changes are detected, check
 * for Homekit tags and, if present, add the items to the HomekitAccessoryRegistry.
 * 
 * @author Andy Lintner
 */
public class HomekitChangeListener implements ItemRegistryChangeListener {

    private ItemRegistry itemRegistry;
    private HomekitAccessoryUpdater updater = new HomekitAccessoryUpdater();
    private Logger logger = LoggerFactory.getLogger(HomekitChangeListener.class);
    private final HomekitAccessoryRegistry accessoryRegistry = new HomekitAccessoryRegistry();
    private HomekitSettings settings;

    @Override
    public synchronized void added(Item item) {
        HomekitTaggedItem taggedItem = new HomekitTaggedItem(item);
        if (taggedItem.isTagged()) {
            if (taggedItem.isRootDevice()) {
                createRootDevice(taggedItem);
            } else {
                createCharacteristic(taggedItem);
            }
        }
    }

    @Override
    public void allItemsChanged(Collection<String> oldItemNames) {
        clearAccessories();
    }

    @Override
    public synchronized void removed(Item item) {
        HomekitTaggedItem taggedItem = new HomekitTaggedItem(item);
        if (taggedItem.isTagged()) {
            accessoryRegistry.remove(taggedItem);
        }
    }

    @Override
    public void updated(Item oldElement, Item element) {
        removed(oldElement);
        added(element);
    }

    public synchronized void clearAccessories() {
        accessoryRegistry.clear();
    }

    public synchronized void setBridge(HomekitRoot bridge) {
        accessoryRegistry.setBridge(bridge);
    }

    public synchronized void setItemRegistry(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
        itemRegistry.addRegistryChangeListener(this);
        itemRegistry.getAll().forEach(item -> added(item));
    }

    public void setUpdater(HomekitAccessoryUpdater updater) {
        this.updater = updater;
    }

    public void setSettings(HomekitSettings settings) {
        this.settings = settings;
    }

    private void createRootDevice(HomekitTaggedItem taggedItem) {
        try {
            accessoryRegistry
                    .addRootDevice(HomekitAccessoryFactory.create(taggedItem, itemRegistry, updater, settings));
        } catch (Exception e) {
            logger.error("Could not add device: " + e.getMessage(), e);
        }
    }

    private void createCharacteristic(HomekitTaggedItem taggedItem) {
        accessoryRegistry.addCharacteristic(taggedItem);
    }
}
