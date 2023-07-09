/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.accounts.types;

import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.accounts.AccountType;
import meteordevelopment.meteorclient.utils.network.Http;
import net.minecraft.client.util.Session;

import java.util.Optional;

public class AccessTokenAccount extends Account<AccessTokenAccount> {
    public AccessTokenAccount(String token) {
        super(AccountType.AccessToken, token);
    }

    @Override
    public boolean fetchInfo() {
        return auth();
    }

    @Override
    public boolean login() {
        super.login();

        if (!auth()) return false;

        cache.loadHead();

        setSession(new Session(cache.username, cache.uuid, name, Optional.empty(), Optional.empty(), Session.AccountType.MSA));
        return true;
    }

    private boolean auth() {
        // Check game ownership
        GameOwnershipResponse gameOwnershipRes = Http.get("https://api.minecraftservices.com/entitlements/mcstore")
            .bearer(name)
            .sendJson(GameOwnershipResponse.class);

        if (gameOwnershipRes == null || !gameOwnershipRes.hasGameOwnership()) return false;

        // Profile
        ProfileResponse profileRes = Http.get("https://api.minecraftservices.com/minecraft/profile")
            .bearer(name)
            .sendJson(ProfileResponse.class);

        if (profileRes == null) return false;

        cache.username = profileRes.name;
        cache.uuid = profileRes.id;

        return true;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof MicrosoftAccount || o instanceof AccessTokenAccount) && ((Account<?>) o).getName().equals(name);
    }

    private static class GameOwnershipResponse {
        private GameOwnershipResponse.Item[] items;

        private static class Item {
            private String name;
        }

        private boolean hasGameOwnership() {
            boolean hasProduct = false;
            boolean hasGame = false;

            for (GameOwnershipResponse.Item item : items) {
                if (item.name.equals("product_minecraft")) hasProduct = true;
                else if (item.name.equals("game_minecraft")) hasGame = true;
            }

            return hasProduct && hasGame;
        }
    }

    private static class ProfileResponse {
        public String id;
        public String name;
    }
}
