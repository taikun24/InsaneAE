plugins {
    id("dev.kikugie.stonecutter")
}

// src/ の条件コメントがどのバージョンの状態で置かれているか。IDE で見えるのもこのバージョン。
stonecutter active "1.21.1-neoforge"

stonecutter parameters {
    val loader = current.project.substringAfterLast('-')
    // `//? if forge {` / `//? if neoforge {` で使う。MC の差なら `//? if >=1.21 {` の方を使う。
    constants.match(loader, "forge", "neoforge")

    // Forge と NeoForge でパッケージ名だけが違うもの。src/ には NeoForge の名前で書き、
    // 1.20.1 を作るときに Forge の名前へ戻す。1 対 1 に戻せる組だけを置くこと
    // (例えば net.minecraftforge.common 全体は common.crafting.conditions の行き先が違うので置けない)。
    replacements {
        string(current.parsed >= "1.21") {
            replace("net.minecraftforge.eventbus.api", "net.neoforged.bus.api")
            replace("net.minecraftforge.registries", "net.neoforged.neoforge.registries")
            replace("net.minecraftforge.client", "net.neoforged.neoforge.client")
            replace("net.minecraftforge.fml", "net.neoforged.fml")
            replace("net.minecraftforge.common.data", "net.neoforged.neoforge.common.data")
            replace("net.minecraftforge.event", "net.neoforged.neoforge.event")
            replace("net.minecraftforge.api.distmarker", "net.neoforged.api.distmarker")
            replace("net.minecraftforge.data", "net.neoforged.neoforge.data")
            replace("net.minecraftforge.server", "net.neoforged.neoforge.server")
            replace("net.minecraftforge.items", "net.neoforged.neoforge.items")
            replace("net.minecraftforge.common.MinecraftForge", "net.neoforged.neoforge.common.NeoForge")
            replace("MinecraftForge.EVENT_BUS", "NeoForge.EVENT_BUS")
        }
    }
}
