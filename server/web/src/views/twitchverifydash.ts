import {customElement, html, LitElement, property} from 'lit-element';

import 'weightless/progress-spinner'

// @ts-ignore
import DashboardStyle from '../style/dashboard.scss';

@customElement('twitch-verify-dash')
export class TwitchVerifyDash extends LitElement {

    @property()
    private streams: any[] | undefined;

    protected render() {
        return html`
<header><h6>Verify Twitch Streams</h6></header>
<div class="c-centerCard">
    <div class="c-parentCard mdc-card">
        <div class="c-tableRow">
             <div class="c-tableCell c-tableHeader">Discord Username</div>
             <div class="c-tableCell c-tableHeader">Twitch Username</div>
             <div class="c-tableCell c-tableHeader c-center">Live Now?</div>
             <div class="c-tableCell c-tableHeader c-center">Announce?</div>
        </div>
        ${this.streams == undefined ? html`<wl-progress-spinner></wl-progress-spinner>` : this.streams.map(this.generateStreamRow)}
    </div>
</div>`
    }

    generateStreamRow=(stream: any)=> {
        // language=HTML
        return html`<div class="mdc-card c-tableRow" style="${stream.notify == 1 ? 'background-color: yellow' : ''}">
            <div class="c-tableCell"><div class="c-image-cropper c-middleInline"><img src="${stream.discordAvatarUrl}"></div> ${stream.discordName}</div>
            <div class="c-tableCell"><div class="c-image-cropper c-middleInline"><img src="${stream.twitchAvatarUrl}"></div> ${stream.twitchName}</div>
            <div class="c-tableCell c-center"><div class="c-ledSocket c-middleInline" style="background-color: ${stream.live ? 'green' : 'red'}"></div></div>
            <div class="c-tableCell c-center">
                <div class="mdc-checkbox c-purplecheck">
                    <input type="checkbox" class="mdc-checkbox__native-control" @change=${(e: Event) => {this.handleCheck(e, stream)}} ?checked=${stream.notify == 2}>
                        <div class="mdc-checkbox__background">
                            <svg class="mdc-checkbox__checkmark" viewBox="0 0 24 24"><path class="mdc-checkbox__checkmark-path" fill="none" d="M1.73,12.91 8.1,19.28 22.79,4.59"/></svg>
                            <div class="mdc-checkbox__mixedmark"></div>
                        </div>
                </div>
            </div>    
</div>`
    };

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
            .then(json => this.streams = json);
    };

    firstUpdated(_changedProperties: any) {
        this.fetchStreams();
    }

    static get styles() {
        return [DashboardStyle];
    }
}