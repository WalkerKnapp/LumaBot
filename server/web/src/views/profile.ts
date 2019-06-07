import {html, LitElement, property, css} from 'lit-element';
import '@polymer/paper-card/paper-card.js';
import '@polymer/paper-button/paper-button.js';

class Profile extends LitElement {

    @property({type: String})
    private discrimName: String | undefined;

    @property({type: String})
    private avatarUrl: String | undefined;

    @property({type: String})
    private verified: boolean | undefined;

    @property({type: String})
    private steamAccounts: String[] | undefined;

    @property({type: String})
    private srcomAccounts: String[] | undefined;

    protected render() {
        console.log("rendering profile");
        return html`
<div style="display: flex;justify-content: center;align-items: center; margin-top: 70px;">
    <paper-card>
        <div class="image-cropper"><img src="${this.avatarUrl}"></div>
        <h1>${this.discrimName}
        ${this.verified ?
            html`
<svg style="display: inline;" width="30" height="30" viewBox="0 0 48 48" fill="none" stroke="green" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <polyline points="40 12 18 34 8 24"></polyline>
</svg>` : ''}</h1>
        <hr>
        <h2>Steam:</h2>
        ${this.generateSteamTable()}
        <paper-button style="margin-right: 1em;" class="gray">Add Steam Account</paper-button>
        <h2>Speedrun.com:</h2>
        ${this.generateSrcomTable()}
        <paper-button style="margin-right: 1em;" class="gold">Add Speedrun.com Account</paper-button>
        
    </paper-card>
</div>
        `
    }

    protected generateSteamTable(){
        if(this.steamAccounts != undefined){
            return this.steamAccounts[0];
        } else {
            return "";
        }
    }

    protected generateSrcomTable(){
        if(this.srcomAccounts != undefined){
            return this.srcomAccounts[0];
        } else {
            return "";
        }
    }


    static get styles() {
        return css`
        .gray {
            background-color: #609dd2;
            --paper-button-ink-color: gray; !important;
            --paper-button-flat-keyboard-focus: {
                background-color: gray; !important;
                color: white !important;
            }
        }
        .gold {
            background-color: #f6ce55;
            --paper-button-ink-color: gray; !important;
            --paper-button-flat-keyboard-focus: {
                background-color: gray; !important;
                color: white !important;
            }
        }
        paper-card {
            font-family: "Roboto", "Helvetica", "Arial", sans-serif;
            font-weight: bold;
        }
        h1 {
            font-size: 2rem;
            display: flex;
            align-items: center;
        }
        h2 {
            font-size: 1.5rem;
            display: flex;
            align-items: center;
        }
        paper-card {
            width: 85%;
            padding: 10px;
        }
        .image-cropper {
            width: 200px;
            height: 200px;
            position: relative;
            overflow: hidden;
            border-radius:50%;
            margin-left: auto;
            margin-right: auto;
        }
        img {
            display: inline;
            margin: 0 auto;
            height: 100%;
            width: auto;
        }
        `
    }
}

window.customElements.define("user-profile", Profile);