package mace.mod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Use AutoConfigClient to avoid the deprecated AutoConfig wrapper
        // Pass your config class directly, NOT a holder or holder.getClass()
        return parent -> AutoConfigClient.getConfigScreen(McModConfig.class, parent).get();
    }
}