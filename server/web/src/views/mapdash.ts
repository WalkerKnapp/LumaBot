import {customElement, html, LitElement, property} from 'lit-element';
import {Map} from 'ol';
import TileLayer from 'ol/layer/Tile';
import OSM from "ol/source/OSM";

import 'weightless/progress-spinner'

// @ts-ignore
import DashboardStyle from '../style/dashboard.scss';


@customElement('map-dash')
export class MapDash extends LitElement {

    @property()
    private locations: any[] | undefined;

    protected render() {
        return html`
<header><h6>Map</h6></header>
<div class="c-centerCard">
    <div class="c-parentCard mdc-card">
        <div id="map"></div>
        ${this.locations == undefined ? html`<wl-progress-spinner></wl-progress-spinner>` : html``}
    </div>
</div>`
    }

    handleCheck=(e: Event, stream: any)=>{
        let element: Element | null = e.srcElement;
        if(element != null && element instanceof HTMLInputElement) {
            let inputElement: HTMLInputElement = element;
            fetch('/user/' + stream.discordId + '/connections/twitch/' + stream.twitchId, {
                method: 'PATCH',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'text/plain'
                },
                body: JSON.stringify({notify: inputElement.checked ? 2 : 0})
            });

            if (inputElement.checked) {
                stream.notify = 1;
            } else {
                stream.notify = 0;
            }
        }
    };

    fetchStreams=()=>{
        fetch('/connections/twitch', {
            method: 'GET',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'text/plain'
            }
        }).then(response => response.json())
            .then(json => this.locations = json);
    };

    updated(_changedProperties: any) {
        new Map({
            target: 'map',
            layers: [
                new TileLayer({
                    preload: 4,
                    source: new OSM()
                })
            ],
            loadTilesWhileAnimating: true
        })
    }

    firstUpdated(_changedProperties: any) {
        //this.fetchStreams();
    }

    static get styles() {
        return [DashboardStyle];
    }
}