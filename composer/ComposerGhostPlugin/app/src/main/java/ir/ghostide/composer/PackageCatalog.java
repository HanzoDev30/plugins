package ir.ghostide.composer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The curated list shown when the panel opens, so the common cases need no typing at all.
 *
 * <p>Every entry is a plain {@code vendor/name}; the install button turns it into {@code composer
 * require} and prepends the PHP/Composer guard, so a missing runtime is installed first instead of
 * failing halfway through.
 */
final class PackageCatalog {

  private PackageCatalog() {}

  static final class Entry {
    final String name;
    final String summary;

    Entry(String name, String summary) {
      this.name = name;
      this.summary = summary;
    }
  }

  static final class Group {
    final String title;
    final List<Entry> entries;

    Group(String title, List<Entry> entries) {
      this.title = title;
      this.entries = entries;
    }
  }

  static List<Group> groups() {
    List<Group> groups = new ArrayList<>();

    groups.add(
        new Group(
            "Essentials",
            Arrays.asList(
                new Entry("composer/composer", "The dependency manager itself"),
                new Entry("guzzlehttp/guzzle", "HTTP client for PHP"),
                new Entry("monolog/monolog", "Logging"),
                new Entry("psr/log", "The logging interface everyone implements"),
                new Entry("vlucas/phpdotenv", "Reads .env files"),
                new Entry("symfony/var-dumper", "Var dump that is actually readable"),
                new Entry("symfony/console", "CLI components and commands"),
                new Entry("ramsey/uuid", "RFC 4122 UUIDs"))));

    groups.add(
        new Group(
            "Frameworks",
            Arrays.asList(
                new Entry("laravel/framework", "The Laravel framework"),
                new Entry("laravel/laravel", "Laravel project skeleton"),
                new Entry("symfony/symfony", "The Symfony framework"),
                new Entry("slim/slim", "Micro framework"),
                new Entry("cakephp/cakephp", "The CakePHP framework"),
                new Entry("yiisoft/yii2", "The Yii 2 framework"))));

    groups.add(
        new Group(
            "Database & Query",
            Arrays.asList(
                new Entry("illuminate/database", "Eloquent ORM without the whole framework"),
                new Entry("doctrine/orm", "The Doctrine ORM"),
                new Entry("doctrine/dbal", "Database abstraction and schema builder"),
                new Entry(
                    "spatie/laravel-eloquent-query-builder", "Readable Eloquent query builder"),
                new Entry("phpmyadmin/sql-parser", "Parses and formats SQL"),
                new Entry("fakerphp/faker", "Fake data for seeds and tests"))));

    groups.add(
        new Group(
            "Web & API",
            Arrays.asList(
                new Entry("laravel/sanctum", "API token authentication"),
                new Entry("fruitcake/php-cors", "CORS handling"),
                new Entry("nyholm/psr7", "PSR-7 request and response objects"),
                new Entry("php-http/guzzle7-adapter", "Guzzle behind the HTTPlug interface"),
                new Entry("league/flysystem", "Filesystem abstraction"),
                new Entry("intervention/image", "Image manipulation"))));

    groups.add(
        new Group(
            "Dev & Testing",
            Arrays.asList(
                new Entry("phpunit/phpunit", "The PHP test runner"),
                new Entry("mockery/mockery", "Mock objects for tests"),
                new Entry("friendsofphp/php-cs-fixer", "Coding style fixer"),
                new Entry("phpstan/phpstan", "Static analysis"),
                new Entry("phpstan/phpstan-doctrine", "PHPStan rules for Doctrine"),
                new Entry("vimeo/psalm", "Static analysis with a type engine"),
                new Entry("spatie/laravel-debugbar", "Debug toolbar for Laravel"),
                new Entry("beyondcode/livewire", "Reactive components for Laravel"))));

    return groups;
  }
}
