package zservers.zsend.command;

import zservers.zslib.network.ZAcknowledge;
import zservers.zslib.network.ZCommand;
import zservers.zsend.main.ZSend;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.plugin.Command;
import static zservers.zlib.utilities.Utilities.*;

public class Dispatch extends Command {

	private static ZSend main = ZSend.getInstance();

	public Dispatch() {

		super("dispatch", null, "dsp");
	}

	@Override
	public void execute(CommandSender sender, String[] args) {

		if(!sender.hasPermission(ZSend.getInstance().getConfig().getString(ZSend.ADMIN_PERM_PATH))) {

			sender.sendMessage(new ComponentBuilder("You don't have permission to perform this command!").color(ChatColor.RED).create());
			return;
		}

		String line;
		if(args.length < 1) {

			sender.sendMessage(new ComponentBuilder("/dispatch <command String>...").color(ChatColor.RED).create());
			return;
		}

		line = joinString(args, " ");

		try {

			ZAcknowledge acknowledge = main.dispatchCommand(new ZCommand(line));

			main.inf("Dispatched " + acknowledge.info);
			main.inf("State " + acknowledge.state + " >> " + acknowledge.message);

		} catch (Exception ex) {

			main.err(ex, "Exception whilst dispatching command");
		}
	}
}
