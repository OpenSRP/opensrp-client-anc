package org.smartregister.anc.library;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;
import com.vijay.jsonwizard.constants.JsonFormConstants;

import net.sqlcipher.database.SQLiteDatabase;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.EventBusBuilder;
import org.greenrobot.eventbus.meta.SubscriberInfoIndex;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.AllConstants;
import org.smartregister.Context;
import org.smartregister.CoreLibrary;
import org.smartregister.anc.library.activity.ActivityConfiguration;
import org.smartregister.anc.library.domain.YamlConfig;
import org.smartregister.anc.library.domain.YamlConfigItem;
import org.smartregister.anc.library.helper.AncRulesEngineHelper;
import org.smartregister.anc.library.helper.ECSyncHelper;
import org.smartregister.anc.library.repository.ContactTasksRepositoryHelper;
import org.smartregister.anc.library.repository.PartialContactRepositoryHelper;
import org.smartregister.anc.library.repository.PatientRepositoryHelper;
import org.smartregister.anc.library.repository.PreviousContactRepositoryHelper;
import org.smartregister.anc.library.repository.RegisterRepository;
import org.smartregister.anc.library.util.ConstantsUtils;
import org.smartregister.anc.library.util.FilePathUtils;
import org.smartregister.configurableviews.helper.JsonSpecHelper;
import org.smartregister.domain.Setting;
import org.smartregister.repository.DetailsRepository;
import org.smartregister.repository.EventClientRepository;
import org.smartregister.repository.UniqueIdRepository;
import org.smartregister.sync.ClientProcessorForJava;
import org.smartregister.view.activity.DrishtiApplication;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStreamReader;

import id.zelory.compressor.Compressor;
import timber.log.Timber;

/**
 * Created by Ephraim Kigamba - ekigamba@ona.io on 2019-07-02
 */

public class AncLibrary {
    private static AncLibrary instance;
    private final Context context;
    private JsonSpecHelper jsonSpecHelper;
    private PartialContactRepositoryHelper partialContactRepositoryHelper;
    private PreviousContactRepositoryHelper previousContactRepositoryHelper;
    private ContactTasksRepositoryHelper contactTasksRepositoryHelper;
    private EventClientRepository eventClientRepository;
    private UniqueIdRepository uniqueIdRepository;
    private DetailsRepository detailsRepository;

    private ECSyncHelper ecSyncHelper;
    private AncRulesEngineHelper ancRulesEngineHelper;
    private RegisterRepository registerRepository;
    private ClientProcessorForJava clientProcessorForJava;
    private JSONObject defaultContactFormGlobals = new JSONObject();

    private Compressor compressor;
    private Gson gson;

    private Yaml yaml;

    private SubscriberInfoIndex subscriberInfoIndex;

    private int databaseVersion;
    private ActivityConfiguration activityConfiguration;

    private AncLibrary(@NonNull Context context, int dbVersion, @NonNull ActivityConfiguration activityConfiguration, @Nullable SubscriberInfoIndex subscriberInfoIndex) {
        this.context = context;
        this.subscriberInfoIndex = subscriberInfoIndex;
        this.databaseVersion = dbVersion;
        this.activityConfiguration = activityConfiguration;

        //Initialize JsonSpec Helper
        this.jsonSpecHelper = new JsonSpecHelper(getApplicationContext());
        setUpEventHandling();

        //initialize configs processor
        initializeYamlConfigs();
    }

    private AncLibrary(@NonNull Context contextArg, int dbVersion, @NonNull ActivityConfiguration activityConfiguration, @Nullable SubscriberInfoIndex subscriberInfoIndex, RegisterRepository registerRepository) {
        this.context = contextArg;
        this.subscriberInfoIndex = subscriberInfoIndex;
        this.databaseVersion = dbVersion;
        this.activityConfiguration = activityConfiguration;
        this.registerRepository = registerRepository;

        //Initialize JsonSpec Helper
        this.jsonSpecHelper = new JsonSpecHelper(getApplicationContext());
        setUpEventHandling();

        //initialize configs processor
        initializeYamlConfigs();

        this.registerRepository = registerRepository;
    }

    public android.content.Context getApplicationContext() {
        return context.applicationContext();
    }

    private void setUpEventHandling() {
        try {
            EventBusBuilder eventBusBuilder = EventBus.builder()
                    .addIndex(new ANCEventBusIndex());

            if (subscriberInfoIndex != null) {
                eventBusBuilder.addIndex(subscriberInfoIndex);
            }

            eventBusBuilder.installDefaultEventBus();
        } catch (Exception e) {
            Timber.e(e, " --> setUpEventHandling");
        }
    }

    private void initializeYamlConfigs() {
        Constructor constructor = new Constructor(YamlConfig.class);
        TypeDescription customTypeDescription = new TypeDescription(YamlConfig.class);
        customTypeDescription.addPropertyParameters(YamlConfigItem.FIELD_CONTACT_SUMMARY_ITEMS, YamlConfigItem.class);
        constructor.addTypeDescription(customTypeDescription);
        yaml = new Yaml(constructor);
    }

    public static void init(@NonNull Context context, int dbVersion) {
        init(context, dbVersion, new ActivityConfiguration());
    }

    public static void init(@NonNull Context context, int dbVersion, @NonNull ActivityConfiguration activityConfiguration) {
        init(context, dbVersion, activityConfiguration, null);
    }

    public static void init(@NonNull Context context, int dbVersion, @NonNull ActivityConfiguration activityConfiguration, @Nullable SubscriberInfoIndex subscriberInfoIndex) {
        if (instance == null) {
            instance = new AncLibrary(context, dbVersion, activityConfiguration, subscriberInfoIndex);
        }
    }

    public static void init(@NonNull Context context, int dbVersion, @NonNull ActivityConfiguration activityConfiguration, @Nullable SubscriberInfoIndex subscriberInfoIndex, @Nullable RegisterRepository registerRepository) {
        if (instance == null) {
            instance = new AncLibrary(context, dbVersion, activityConfiguration, subscriberInfoIndex, registerRepository);
        }
    }

    public static void init(@NonNull Context context, int dbVersion, @Nullable SubscriberInfoIndex subscriberInfoIndex) {
        init(context, dbVersion, new ActivityConfiguration(), subscriberInfoIndex);
    }

    public static JsonSpecHelper getJsonSpecHelper() {
        return getInstance().jsonSpecHelper;
    }

    public static AncLibrary getInstance() {
        if (instance == null) {
            throw new IllegalStateException(" Instance does not exist!!! Call "
                    + AncLibrary.class.getName()
                    + ".init method in the onCreate method of "
                    + "your Application class ");
        }
        return instance;
    }

    /**
     * This method should be called in onUpgrade method of the Repository class where the migrations
     * are already managed instead of writing new code to manage them.
     */
    public static void performMigrations(@NonNull SQLiteDatabase database) {
        PatientRepositoryHelper.performMigrations(database);
    }

    public PartialContactRepositoryHelper getPartialContactRepositoryHelper() {
        if (partialContactRepositoryHelper == null) {
            partialContactRepositoryHelper = new PartialContactRepositoryHelper();
        }

        return partialContactRepositoryHelper;
    }

    public PreviousContactRepositoryHelper getPreviousContactRepositoryHelper() {
        if (previousContactRepositoryHelper == null) {
            previousContactRepositoryHelper = new PreviousContactRepositoryHelper();
        }

        return previousContactRepositoryHelper;
    }

    public ContactTasksRepositoryHelper getContactTasksRepositoryHelper() {
        if (contactTasksRepositoryHelper == null) {
            contactTasksRepositoryHelper = new ContactTasksRepositoryHelper();
        }

        return contactTasksRepositoryHelper;
    }

    public EventClientRepository getEventClientRepository() {
        if (eventClientRepository == null) {
            eventClientRepository = new EventClientRepository();
        }
        return eventClientRepository;
    }

    public UniqueIdRepository getUniqueIdRepository() {
        if (uniqueIdRepository == null) {
            uniqueIdRepository = new UniqueIdRepository();
        }

        return uniqueIdRepository;
    }

    public AncRulesEngineHelper getAncRulesEngineHelper() {
        if (ancRulesEngineHelper == null) {
            ancRulesEngineHelper = new AncRulesEngineHelper(getApplicationContext());
        }
        return ancRulesEngineHelper;
    }

    public ECSyncHelper getEcSyncHelper() {
        if (ecSyncHelper == null) {
            ecSyncHelper = ECSyncHelper.getInstance(getApplicationContext());
        }
        return ecSyncHelper;
    }

    public Compressor getCompressor() {
        if (compressor == null) {
            compressor = new Compressor(getApplicationContext());
        }
        return compressor;
    }

    public ClientProcessorForJava getClientProcessorForJava() {
        if (clientProcessorForJava == null) {
            clientProcessorForJava = DrishtiApplication.getInstance().getClientProcessor();
        }

        return clientProcessorForJava;
    }

    public DetailsRepository getDetailsRepository() {
        if (detailsRepository == null) {
            detailsRepository = CoreLibrary.getInstance().context().detailsRepository();
        }

        return detailsRepository;
    }

    public Gson getGsonInstance() {
        if (gson == null) {
            gson = new Gson();
        }
        return gson;
    }

    public void populateGlobalSettings() {

        Setting setting = getCharacteristics(ConstantsUtils.PrefKeyUtils.SITE_CHARACTERISTICS);
        Setting populationSetting = getCharacteristics(ConstantsUtils.PrefKeyUtils.POPULATION_CHARACTERISTICS);

        populateGlobalSettingsCore(setting);
        populateGlobalSettingsCore(populationSetting);
    }

    public Setting getCharacteristics(String characteristics) {
        return AncLibrary.getInstance().getContext().allSettings().getSetting(characteristics);
    }

    private void populateGlobalSettingsCore(Setting setting) {
        try {
            JSONObject settingObject = setting != null ? new JSONObject(setting.getValue()) : null;
            if (settingObject != null) {
                JSONArray settingArray = settingObject.getJSONArray(AllConstants.SETTINGS);
                if (settingArray != null) {

                    for (int i = 0; i < settingArray.length(); i++) {

                        JSONObject jsonObject = settingArray.getJSONObject(i);
                        Boolean value = jsonObject.optBoolean(JsonFormConstants.VALUE);
                        JSONObject nullObject = null;
                        if (value != null && !value.equals(nullObject)) {
                            defaultContactFormGlobals.put(jsonObject.getString(JsonFormConstants.KEY), value);
                        } else {

                            defaultContactFormGlobals.put(jsonObject.getString(JsonFormConstants.KEY), false);
                        }
                    }


                }
            }
        } catch (JSONException e) {
            Timber.e(" --> populateGlobalSettingsCore");
        }
    }

    public Context getContext() {
        return context;
    }

    public JSONObject getDefaultContactFormGlobals() {
        return defaultContactFormGlobals;
    }

    public Iterable<Object> readYaml(String filename) throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(
                getApplicationContext().getAssets().open((FilePathUtils.FolderUtils.CONFIG_FOLDER_PATH + filename)));
        return yaml.loadAll(inputStreamReader);
    }

    public int getDatabaseVersion() {
        return databaseVersion;
    }

    @NonNull
    public ActivityConfiguration getActivityConfiguration() {
        return activityConfiguration;
    }

    public RegisterRepository getRegisterRepository() {
        if (registerRepository == null) {
            registerRepository = new RegisterRepository();
        }
        return registerRepository;
    }

    public void setRegisterRepository(RegisterRepository registerRepository) {
        this.registerRepository = registerRepository;
    }
}
