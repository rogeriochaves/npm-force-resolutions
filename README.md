# NPM Force Resolutions

This packages modifies package-lock.json to force the installation of specific version of a transitive dependency (dependency of dependency), similar to yarn's [selective dependency resolutions](https://yarnpkg.com/lang/en/docs/selective-version-resolutions/), but without having to migrate to yarn.

## WARNING before you start

The use case for this is when there is a security vulnerability and you MUST update a nested dependency otherwise your project would be vulnerable. But this should only be used as a last resource, you should first update your top-level dependencies and fill an issue for them to update the vulnerable sub-dependencies (`npm ls <vulnerable dependency>` can help you with that).

# How to use

# Contributing

To build the project from source you'll need to install [clojure](https://clojure.org/guides/getting_started). Then you can run:

```
npm install
npm run build
```
