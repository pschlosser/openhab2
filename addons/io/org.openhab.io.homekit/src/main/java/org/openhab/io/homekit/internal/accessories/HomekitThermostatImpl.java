package org.openhab.io.homekit.internal.accessories;

import java.util.concurrent.CompletableFuture;

import org.eclipse.smarthome.core.items.GenericItem;
import org.eclipse.smarthome.core.items.Item;
import org.eclipse.smarthome.core.items.ItemRegistry;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.openhab.io.homekit.internal.HomekitAccessoryUpdater;
import org.openhab.io.homekit.internal.HomekitSettings;
import org.openhab.io.homekit.internal.HomekitTaggedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beowulfe.hap.HomekitCharacteristicChangeCallback;
import com.beowulfe.hap.accessories.Thermostat;
import com.beowulfe.hap.accessories.properties.TemperatureUnit;
import com.beowulfe.hap.accessories.properties.ThermostatMode;

public class HomekitThermostatImpl extends AbstractHomekitAccessoryImpl implements Thermostat, GroupedAccessory {

    private final String groupName;
    private final HomekitSettings settings;
    private String currentTemperatureItemName;
    private String heatingThresholdItemName;
    private String coolingThresholdItemName;
    private String heatingCoolingModeItemName;
    private String autoThresholdItemName;

    private Logger logger = LoggerFactory.getLogger(HomekitThermostatImpl.class);

    public HomekitThermostatImpl(HomekitTaggedItem taggedItem, ItemRegistry itemRegistry,
            HomekitAccessoryUpdater updater, HomekitSettings settings) {
        super(taggedItem, itemRegistry, updater);
        this.groupName = taggedItem.getItem().getName();
        this.settings = settings;
    }

    @Override
    public String getGroupName() {
        return groupName;
    }

    @Override
    public void addCharacteristic(HomekitTaggedItem item) {
        switch (item.getCharacteristicType()) {
            case COOLING_THRESHOLD:
                coolingThresholdItemName = item.getItem().getName();
                break;

            case CURRENT_TEMPERATURE:
                currentTemperatureItemName = item.getItem().getName();
                break;

            case HEATING_COOLING_MODE:
                heatingCoolingModeItemName = item.getItem().getName();
                break;

            case HEATING_THRESHOLD:
                heatingThresholdItemName = item.getItem().getName();
                break;

            case AUTO_THRESHOLD:
                autoThresholdItemName = item.getItem().getName();
                break;

            default:
                logger.error("Unrecognized thermostat characteristic: " + item.getCharacteristicType().name());
                break;

        }
    }

    @Override
    public boolean isComplete() {
        return coolingThresholdItemName != null && heatingThresholdItemName != null
                && currentTemperatureItemName != null && heatingCoolingModeItemName != null;
    }

    @Override
    public CompletableFuture<Double> getCoolingThresholdTemperature() {
        Item item = getItemRegistry().get(coolingThresholdItemName);
        DecimalType state = (DecimalType) item.getStateAs(DecimalType.class);
        if (state == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(convertToCelsius(state.doubleValue()));
    }

    @Override
    public CompletableFuture<ThermostatMode> getCurrentMode() {
        Item item = getItemRegistry().get(heatingCoolingModeItemName);
        StringType state = (StringType) item.getStateAs(StringType.class);
        ThermostatMode mode;
        if (state != null) {
            String stringValue = state.toString();

            if (stringValue.equals(settings.getThermostatCoolMode())) {
                mode = ThermostatMode.COOL;
            } else if (stringValue.equals(settings.getThermostatHeatMode())) {
                mode = ThermostatMode.HEAT;
            } else if (stringValue.equals(settings.getThermostatAutoMode())) {
                mode = ThermostatMode.AUTO;
            } else if (stringValue.equals(settings.getThermostatOffMode())) {
                mode = ThermostatMode.OFF;
            } else {
                logger.error("Unrecognized heating cooling target mode: " + stringValue
                        + ". Expected cool, heat, auto, or off strings in value.");
                mode = ThermostatMode.OFF;
            }
        } else {
            logger.info("Heating cooling target mode not available. Relaying value of OFF to Homekit");
            mode = ThermostatMode.OFF;
        }
        return CompletableFuture.completedFuture(mode);
    }

    @Override
    public CompletableFuture<Double> getCurrentTemperature() {
        Item item = getItemRegistry().get(currentTemperatureItemName);
        DecimalType state = (DecimalType) item.getStateAs(DecimalType.class);
        if (state == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(convertToCelsius(state.doubleValue()));
    }

    @Override
    public CompletableFuture<Double> getHeatingThresholdTemperature() {
        Item item = getItemRegistry().get(heatingThresholdItemName);
        DecimalType state = (DecimalType) item.getStateAs(DecimalType.class);
        if (state == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(convertToCelsius(state.doubleValue()));
    }

    @Override
    public double getMaximumTemperature() {
        return settings.getMaximumTemperature();
    }

    @Override
    public double getMinimumTemperature() {
        return settings.getMinimumTemperature();
    }

    @Override
    public CompletableFuture<ThermostatMode> getTargetMode() {
        return getCurrentMode();
    }

    @Override
    public CompletableFuture<Double> getTargetTemperature() {
        if (autoThresholdItemName != null) {
            Item item = getItemRegistry().get(autoThresholdItemName);
            DecimalType state = (DecimalType) item.getStateAs(DecimalType.class);
            if (state == null) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.completedFuture(convertToCelsius(state.doubleValue()));
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public TemperatureUnit getTemperatureUnit() {
        return TemperatureUnit.CELSIUS;
    }

    @Override
    public void setCoolingThresholdTemperature(Double value) throws Exception {
        getGenericItem(coolingThresholdItemName).setState(new DecimalType(convertFromCelsius(value)));
    }

    @Override
    public void setHeatingThresholdTemperature(Double value) throws Exception {
        getGenericItem(heatingThresholdItemName).setState(new DecimalType(convertFromCelsius(value)));
    }

    @Override
    public void setTargetMode(ThermostatMode mode) throws Exception {
        String modeString = null;
        switch (mode) {
            case AUTO:
                modeString = settings.getThermostatAutoMode();
                break;

            case COOL:
                modeString = settings.getThermostatCoolMode();
                break;

            case HEAT:
                modeString = settings.getThermostatHeatMode();
                break;

            case OFF:
                modeString = settings.getThermostatOffMode();
                break;
        }
        getGenericItem(heatingCoolingModeItemName).setState(new StringType(modeString));
    }

    @Override
    public void setTargetTemperature(Double value) throws Exception {
        getGenericItem(autoThresholdItemName).setState(new DecimalType(convertFromCelsius(value)));
    }

    @Override
    public void subscribeCoolingThresholdTemperature(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getGenericItem(coolingThresholdItemName), callback);
    }

    @Override
    public void subscribeCurrentMode(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getGenericItem(heatingCoolingModeItemName), callback);
    }

    @Override
    public void subscribeCurrentTemperature(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getGenericItem(currentTemperatureItemName), callback);
    }

    @Override
    public void subscribeHeatingThresholdTemperature(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getGenericItem(heatingThresholdItemName), callback);
    }

    @Override
    public void subscribeTargetMode(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getGenericItem(heatingCoolingModeItemName), callback);
    }

    @Override
    public void subscribeTargetTemperature(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getGenericItem(autoThresholdItemName), callback);
    }

    @Override
    public void unsubscribeCoolingThresholdTemperature() {
        getUpdater().unsubscribe(getGenericItem(coolingThresholdItemName));
    }

    @Override
    public void unsubscribeCurrentMode() {
        getUpdater().unsubscribe(getGenericItem(heatingCoolingModeItemName));
    }

    @Override
    public void unsubscribeCurrentTemperature() {
        getUpdater().unsubscribe(getGenericItem(currentTemperatureItemName));
    }

    @Override
    public void unsubscribeHeatingThresholdTemperature() {
        getUpdater().unsubscribe(getGenericItem(heatingThresholdItemName));
    }

    @Override
    public void unsubscribeTargetMode() {
        getUpdater().unsubscribe(getGenericItem(heatingCoolingModeItemName));
    }

    @Override
    public void unsubscribeTargetTemperature() {
        getUpdater().unsubscribe(getGenericItem(autoThresholdItemName));
    }

    private GenericItem getGenericItem(String name) {
        Item item = getItemRegistry().get(name);
        if (item == null) {
            return null;
        }
        if (!(item instanceof GenericItem)) {
            throw new RuntimeException("Expected GenericItem, found " + item.getClass().getCanonicalName());
        }
        return (GenericItem) item;
    }

    private double convertToCelsius(double degrees) {
        if (settings.useFahrenheitTemperature()) {
            return Math.round((5d / 9d) * (degrees - 32d) * 1000d) / 1000d;
        } else {
            return degrees;
        }
    }

    private double convertFromCelsius(double degrees) {
        if (settings.useFahrenheitTemperature()) {
            return Math.round((((9d / 5d) * degrees) + 32d) * 1000d) / 1000d;
        } else {
            return degrees;
        }
    }

}