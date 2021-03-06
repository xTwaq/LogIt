package io.github.lucaseasedup.logit.command.hub;

import static io.github.lucaseasedup.logit.message.MessageHelper.sendMsg;
import static io.github.lucaseasedup.logit.message.MessageHelper.t;
import io.github.lucaseasedup.logit.command.CommandAccess;
import io.github.lucaseasedup.logit.command.CommandHelpLine;
import io.github.lucaseasedup.logit.common.FatalReportedException;
import io.github.lucaseasedup.logit.common.GcRunner;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ReloadHubCommand extends HubCommand
{
    public ReloadHubCommand()
    {
        super("reload", new String[] {},
                new CommandAccess.Builder()
                        .permission("logit.reload")
                        .playerOnly(false)
                        .runningCoreRequired(true)
                        .build(),
                new CommandHelpLine.Builder()
                        .command("logit reload")
                        .descriptionLabel("subCmdDesc.reload")
                        .build());
    }
    
    @Override
    public void execute(CommandSender sender, String[] args)
    {
        try
        {
            getCore().restart();
            
            new GcRunner().runTaskAsynchronously(getPlugin());
            
            if (sender instanceof Player)
            {
                sendMsg(sender, t("reloadPlugin.success"));
            }
        }
        catch (FatalReportedException ex)
        {
            if (sender instanceof Player)
            {
                sendMsg(sender, t("reloadPlugin.fail"));
            }
            
            if (getConfig("config.yml").getBoolean("terminateUnsafeServer"))
            {
                Bukkit.getServer().shutdown();
            }
        }
    }
}
