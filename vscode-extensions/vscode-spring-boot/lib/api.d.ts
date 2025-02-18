import { Event } from "vscode";
import { LanguageClient } from "vscode-languageclient/node";
import { LiveProcess } from "./notification";

export interface ExtensionAPI {
    readonly client: LanguageClient;

    /**
     * An event which fires on live process is connected. Payload is processKey.
     */
    readonly onDidLiveProcessConnect: Event<LiveProcess>

    /**
     * An event which fires on live process is disconnected. Payload is processKey.
     */
    readonly onDidLiveProcessDisconnect: Event<LiveProcess>

	/**
     * An event which fires on live process data change. Payload is processKey.
     */
	readonly onDidLiveProcessUpdate: Event<LiveProcess>

    /**
     * A command to get live process data.
     */
    readonly getLiveProcessData: (query: SimpleQuery | BeansQuery) => Promise<any>

    /**
     * A command to list all currently connected processes.
     * 
     * Returns a list of processKeys.
     */
    readonly listConnectedProcesses: () => Promise<LiveProcess[]>
}

interface LiveProcessDataQuery {
    /**
     * unique identifier of a connected live process.
     */
    processKey: string;
}

interface SimpleQuery extends LiveProcessDataQuery {
    endpoint: "mappings" | "contextPath" | "port" | "properties";
}

interface BeansQuery extends LiveProcessDataQuery {
    endpoint: "beans";
    /**
     * if provided, return corresponding beans via name.
     */
    beanName?: string;
    dependingOn?: string;
}
