package com.avairebot.orion;

import com.avairebot.orion.cache.CacheManager;
import com.avairebot.orion.commands.CommandHandler;
import com.avairebot.orion.commands.system.EvalCommand;
import com.avairebot.orion.commands.system.SetStatusCommand;
import com.avairebot.orion.commands.utility.*;
import com.avairebot.orion.config.ConfigurationLoader;
import com.avairebot.orion.config.MainConfiguration;
import com.avairebot.orion.contracts.handlers.EventHandler;
import com.avairebot.orion.database.DatabaseManager;
import com.avairebot.orion.handlers.EventTypes;
import com.avairebot.orion.logger.Logger;
import com.avairebot.orion.scheduler.ScheduleManager;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.exceptions.RateLimitedException;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class Orion {

    public final MainConfiguration config;
    public final Logger logger;
    public final CacheManager cache;
    public final DatabaseManager database;
    public final ScheduleManager schedule;

    private JDA jda;

    public Orion() throws IOException {
        this.logger = new Logger(this);
        this.cache = new CacheManager(this);

        ConfigurationLoader configLoader = new ConfigurationLoader();
        this.config = (MainConfiguration) configLoader.load("config.json", MainConfiguration.class);
        if (this.config == null) {
            this.logger.error("Something went wrong while trying to load the configuration, exiting program...");
            System.exit(0);
        }

        try {
            jda = prepareJDA().buildBlocking();
        } catch (LoginException | RateLimitedException | InterruptedException ex) {
            this.logger.error("Something went wrong while trying to connect to Discord, exiting program...");
            this.logger.exception(ex);
            System.exit(0);
        }

        database = new DatabaseManager(this);
        schedule = new ScheduleManager(this);

        this.registerCommands();
        this.registerJobs();
    }

    public JDA getJDA() {
        return jda;
    }

    private void registerCommands() {
        // System
        CommandHandler.register(new EvalCommand(this));
        CommandHandler.register(new SetStatusCommand(this));

        // Utility
        CommandHandler.register(new HelpCommand(this));
        CommandHandler.register(new PingCommand(this));
        CommandHandler.register(new InviteCommand(this));
        CommandHandler.register(new SourceCommand(this));
        CommandHandler.register(new StatsCommand(this));
    }

    private void registerJobs() {
        //
    }

    private JDABuilder prepareJDA() {
        JDABuilder builder = new JDABuilder(AccountType.BOT).setToken(this.config.botAuth().getToken());

        Class[] eventArguments = new Class[1];
        eventArguments[0] = Orion.class;

        for (EventTypes event : EventTypes.values()) {
            try {
                Object instance = event.getInstance().getDeclaredConstructor(eventArguments).newInstance(this);

                if (instance instanceof EventHandler) {
                    builder.addEventListener(instance);
                }
            } catch (InstantiationException | NoSuchMethodException | InvocationTargetException ex) {
                this.logger.error("Invalid listener adapter object parsed, failed to create a new instance!");
                this.logger.exception(ex);
            } catch (IllegalAccessException ex) {
                this.logger.error("An attempt was made to register a event listener called " + event + " but it failed somewhere!");
                this.logger.exception(ex);
            }
        }

        return builder.setAutoReconnect(true);
    }
}
