// Synthetic Core SEDI instrumentation; uses the bundled FortWeb worker.
window.__sediCoreActions = {
    setup: async (runId) => {
    const originalFetch = globalThis.fetch.bind(globalThis);
    globalThis.fetch = async (...args) => {
        const response = await originalFetch(...args);
        const url = typeof args[0] === "string" ? args[0] : args[0].url;
        if (url === "https://appassets.androidplatform.net/pyscript-ci.toml") {
            return {ok: response.ok, status: response.status,
                text: async () => "fort_synthetic_recipient_testing = true\n" + await response.text()};
        }
        return response;
    };
    const {createRuntimeBridge} = await import("https://appassets.androidplatform.net/app/runtime/bridge.js");
    const origin = window.__FORT_RUNTIME_ORIGIN__;
    const ctx = {};
    ctx.newBridge = () => createRuntimeBridge({workerUrl:new URL(origin.workerUrl),
        configUrl:new URL(origin.configUrl), runtimeOriginContract:origin});
    ctx.bridge = ctx.newBridge();
    ctx.request = (method, params = {}) => ctx.bridge.request(method, params, 180000);
    const vault = await ctx.request("vaults.create", {
        name:"synthetic-core-holder-" + runId, passcode:"synthetic-holder-passcode"});
    ctx.vaultId = vault.vault.id;
    await ctx.request("vaults.open", {vaultId:ctx.vaultId, passcode:"synthetic-holder-passcode"});
    ctx.holderAid = (await ctx.request("identifiers.create", {vaultId:ctx.vaultId, alias:"holder"})).identifier.aid;
    ctx.registry = (await ctx.request("registries.create", {
        vaultId:ctx.vaultId, issuerAid:ctx.holderAid, name:"presentations"})).registry;
    globalThis.__coreSediTest = ctx;
    return JSON.stringify({holderAid:ctx.holderAid, registryId:ctx.registry.registryId});
    },
    prepare: async (fixtureJSON) => {
    let stage = "setup";
    try {
    const fixture = JSON.parse(fixtureJSON);
    const ctx = globalThis.__coreSediTest;
    const request = ctx.request;
    ctx.fixture = fixture;
    const vaultId = ctx.vaultId;
    stage = "core-schema";
    await request("schemas.import", {vaultId, schemaId:fixture.evidence.schemaId,
        schema:JSON.stringify(fixture.schema)});
    stage = "authority-schemas";
    for (const schema of fixture.authority_schemas) {
        await request("schemas.import", {vaultId, schemaId:schema.$id, schema:JSON.stringify(schema)});
    }
    stage = "remotes";
    for (const [index, evidence] of fixture.supporting.entries()) {
        await request("remotes.import", {vaultId, alias:"authority-" + index, cesr:evidence.kel});
    }
    await request("remotes.import", {vaultId, alias:"issuer", cesr:fixture.issuer});
    await request("remotes.import", {vaultId, alias:"verifier", cesr:fixture.verifierKel});
    stage = "receipt";
    const received = await request("credentials.receive", {vaultId, syntheticOnly:true,
        issuerAid:fixture.evidence.issuerAid, recipientAid:ctx.holderAid,
        schemaId:fixture.evidence.schemaId, evidence:fixture.evidence});
    if (received.credential.issuance !== "verified") throw new Error("Core receipt was not verified");
    ctx.params = {vaultId, syntheticOnly:true, credentialSaid:fixture.evidence.said,
        presenterAid:ctx.holderAid, verifierAid:fixture.verifierAid,
        message:"Present Core SEDI", presentationRegistry:"presentations",
        authorityEvidence:fixture.supporting,
        disclosurePlan:[[fixture.evidence.schemaId, "/", [""]],
            [fixture.supporting[0].schemaId, "/e/utahAgent/_/", [""]],
            [fixture.supporting[1].schemaId, "/e/utahAgent/_/e/orgUnit/_/", [""]]]};
    stage = "negative-preflight";
    const before = await request("identifiers.export", {vaultId, aid:ctx.holderAid});
    const registryBefore = await request("registries.get", {vaultId, name:"presentations"});
    for (const invalid of [
        {authorityEvidence:fixture.supporting.slice(0, 1)},
        {authorityEvidence:fixture.supporting.slice(1)},
        {disclosurePlan:ctx.params.disclosurePlan.slice(0, 2)}
    ]) {
        let rejected = false;
        try { await request("ipex.present", {...ctx.params, ...invalid}); } catch (_) { rejected = true; }
        if (!rejected) throw new Error("Invalid Core presentation was accepted");
        const after = await request("identifiers.export", {vaultId, aid:ctx.holderAid});
        if (JSON.stringify(after) !== JSON.stringify(before)) throw new Error("Rejected preflight changed holder KEL");
        const registryAfter = await request("registries.get", {vaultId, name:"presentations"});
        if (JSON.stringify(registryAfter) !== JSON.stringify(registryBefore))
            throw new Error("Rejected preflight changed the registry view");
    }
    stage = "other-grant";
    ctx.other = await request("ipex.present", {...ctx.params, message:"Different Core Grant"});
    return JSON.stringify({invalidRejected:true, holderKelUnchanged:true, registryViewUnchanged:true,
        otherGrant:ctx.other.exchange.said});
    } catch (error) {
        return JSON.stringify({failureStage:stage, message:String(error?.message ?? error),
            code:String(error?.code ?? "")});
    }
    },
    present: async () => {
    const ctx = globalThis.__coreSediTest;
    ctx.result = await ctx.request("ipex.present", ctx.params);
    if (ctx.result.presentation.grantSaid !== ctx.result.exchange.said ||
        ctx.result.presentation.registryId !== ctx.registry.registryId)
        throw new Error("Core presentation binding does not match");
    ctx.result.otherPresentation = ctx.other.presentation;
    return JSON.stringify(ctx.result);
    },
    recover: async () => {
    const ctx = globalThis.__coreSediTest;
    await ctx.request("vaults.close", {vaultId:ctx.vaultId});
    ctx.bridge.destroy();
    ctx.bridge = ctx.newBridge();
    await ctx.request("vaults.open", {vaultId:ctx.vaultId, passcode:"synthetic-holder-passcode"});
    const received = await ctx.request("credentials.getReceived", {
        vaultId:ctx.vaultId, syntheticOnly:true, said:ctx.fixture.evidence.said});
    const canonical = value => value && typeof value === "object"
        ? Array.isArray(value) ? value.map(canonical) : Object.fromEntries(
            Object.keys(value).sort().map(key => [key, canonical(value[key])]))
        : value;
    if (JSON.stringify(canonical(received.evidence)) !== JSON.stringify(canonical(ctx.fixture.evidence)))
        throw new Error("Core credential evidence changed on worker restart");
    const binding = await ctx.request("registries.present", {
        vaultId:ctx.vaultId, name:"presentations", grantSaid:ctx.result.exchange.said});
    if (JSON.stringify(canonical(binding.presentation)) !== JSON.stringify(canonical(ctx.result.presentation)))
        throw new Error("Presentation binding changed on worker restart");
    const exchange = await ctx.request("ipex.get", {
        vaultId:ctx.vaultId, syntheticOnly:true, said:ctx.result.exchange.said});
    if (exchange.exchange.said !== ctx.result.exchange.said) throw new Error("Grant metadata changed");
    await ctx.request("vaults.close", {vaultId:ctx.vaultId});
    ctx.bridge.destroy();
    return JSON.stringify({credentialRecovered:true, bindingRecovered:true, grantMetadataRecovered:true});
    },
};
window.__sediCoreRun = (action, input) => {
    window.__sediCoreResult = null;
    window.__sediCoreActions[action](input).then(
        value => { window.__sediCoreResult = {ok:true, value}; },
        error => { window.__sediCoreResult = {ok:false, message:String(error?.message ?? error)}; }
    );
};
