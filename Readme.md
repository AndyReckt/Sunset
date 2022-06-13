# Sunset
> A Bukkit CommandHandler.

---

## How to setup


#### Create the sunset instance
```java
public class Main {
    @Override
    public void onEnable() {
        // Create your sunset instance
        Sunset sunset = new Sunset(this);
        // Register your class commands
        sunset.registerCommands(new TestCommands());
    }
}
```
#### Registering commands

```
sunset.registerCommands(new TestCommands());
sunset.registerCommands(ClassWithStaticCommands.class);
```

#### Registering a custom Parameter Type

```
sunset.registerType(new CustomType(), CustomType.class);
```

---

## Examples

[Main Class](https://github.com/AndyReckt/Sunset/blob/master/src/main/java/me/andyreckt/sunset/example/Plugin.java)
| [Example Commands](https://github.com/AndyReckt/Sunset/blob/master/src/main/java/me/andyreckt/sunset/example/TestCommands.java)

---

## Contributing
When contributing, please create a pull request with the branch named as follows ``<feature/fix>/<title>``.

To compile, run the maven command: ``mvn clean install``

---

## Contact

- Discord: AndyReckt#0001
- Telegram: [AndyReckt](https://t.me/andyreckt)
